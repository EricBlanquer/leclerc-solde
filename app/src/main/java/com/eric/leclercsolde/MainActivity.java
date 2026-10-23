package com.eric.leclercsolde;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String SITE_URL = "https://www.e-cartecadeau.leclerc/";
    private static final String SHOP_URL = "https://www.opteamcse.fr/shop/offre/eleclerc";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final long POLL_MS = 600L;
    private static final long SCAN_NOTICE_DELAY_MS = 500L;
    private static final String STORE_FILE = "cards.json";
    private static final String PREFS_FILE = "ui";
    private static final String PREF_SECTION_COLLAPSED = "section_collapsed";
    private static final String PACKAGE_SCHEME = "package";

    private static final String STATUS_JS =
            "(function(){function vis(s){var e=document.querySelector(s);"
                    + "return !!e&&!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);}"
                    + "function txt(s){var e=document.querySelector(s);return e?e.textContent.trim():'';}"
                    + "var n=document.querySelector('#it_num_carte'),c=document.querySelector('#it_code_secret');"
                    + "var ta=document.querySelector('textarea[name=\"h-captcha-response\"]');"
                    + "var cap=document.querySelector('#consultingForm iframe');"
                    + "return JSON.stringify({form:vis('#consultingForm'),result:vis('#soldeContainer'),"
                    + "error:vis('#errorContainer'),num:n?n.value.replace(/\\D/g,''):'',code:c?c.value:'',"
                    + "invalid:(n&&n.validationMessage)||(c&&c.validationMessage)||'',"
                    + "captchaRejected:!!cap&&/red/i.test(cap.style.border),"
                    + "token:(ta&&ta.value)?ta.value.length:0,solde:txt('#soldeCarte'),date:txt('#soldeDate'),"
                    + "resultNum:txt('#soldeNumCarte').replace(/\\D/g,''),message:txt('#errorMessage')});})()";

    private static final String SUBMIT_JS =
            "(function(){var b=document.querySelector('button[name=submitConsulting]');"
                    + "if(b){b.click();return 'CLICK';}return 'NO';})()";

    private static final String EXECUTE_JS =
            "(function(){try{window.hcaptcha.execute();return 'EXEC';}catch(e){return 'ERR:'+e.message;}})()";

    private static final String ANOTHER_JS =
            "(function(){var b=document.querySelector('button[name=backConsulting]');"
                    + "if(b){b.click();return 'ANOTHER';}return 'NONE';})()";

    private static final Pattern CAPTCHA_ERROR = Pattern.compile("captcha", Pattern.CASE_INSENSITIVE);

    private enum Phase { IDLE, PREP, WAIT_TOKEN, WAIT_RESULT }

    private final List<Card> cards = new ArrayList<>();
    private final List<Card> queue = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = this::tick;
    private final Runnable scanNoticeRunnable = this::showScanNotice;

    private CardAdapter adapter;
    private JsonStore store;
    private PdfLibrary library;
    private WebView web;
    private TextView banner;
    private TextView sectionCount;
    private TextView sectionToggle;
    private View section;
    private Button btnCheck;
    private Button btnAccess;

    private boolean pageReady = false;
    private Phase phase = Phase.IDLE;
    private int queueIndex = -1;
    private boolean captchaTriggered = false;
    private boolean captchaRetry = false;
    private boolean scanning = false;
    private boolean deleting = false;
    private CharSequence bannerBeforeScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new JsonStore(this, STORE_FILE);
        library = new PdfLibrary(this);
        banner = findViewById(R.id.banner);
        sectionCount = findViewById(R.id.sectionCount);
        sectionToggle = findViewById(R.id.sectionToggle);
        section = findViewById(R.id.section);
        RecyclerView list = findViewById(R.id.list);
        adapter = new CardAdapter(cards, this::showPdf);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        findViewById(R.id.sectionHeader).setOnClickListener(v -> toggleSection());
        applySectionCollapsed(prefs().getBoolean(PREF_SECTION_COLLAPSED, false));
        loadCards();

        web = findViewById(R.id.web);
        setupWebView();

        btnCheck = findViewById(R.id.btnCheck);
        btnAccess = findViewById(R.id.btnAccess);
        Button btnBuy = findViewById(R.id.btnBuy);
        Button btnDeleteEmpty = findViewById(R.id.btnDeleteEmpty);
        Button btnDeleteSelection = findViewById(R.id.btnDeleteSelection);

        btnCheck.setOnClickListener(v -> startCheck());
        btnBuy.setOnClickListener(v -> openShop());
        btnAccess.setOnClickListener(v -> openAccessSettings());
        btnDeleteEmpty.setOnClickListener(v -> confirmDeleteEmpty());
        btnDeleteSelection.setOnClickListener(v -> confirmDeleteSelection());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPdfs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persist();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdown();
        super.onDestroy();
    }

    private void loadCards() {
        try {
            JSONArray array = store.read();
            for (int i = 0; i < array.length(); i++) {
                Card card = Card.fromJson(array.getJSONObject(i));
                if (card.status == Card.Status.CHECKING) {
                    card.status = Card.Status.PENDING;
                }
                cards.add(card);
            }
            sortCards();
            if (!cards.isEmpty()) {
                banner.setText(plural(R.plurals.cards_in_memory, cards.size()));
            }
        } catch (IOException | JSONException ex) {
            banner.setText(getString(R.string.load_failed, message(ex)));
        }
        updateSectionHeader();
    }

    private void persist() {
        try {
            JSONArray array = new JSONArray();
            for (Card card : cards) {
                array.put(card.toJson());
            }
            store.write(array);
        } catch (IOException | JSONException ex) {
            toast(getString(R.string.save_failed, message(ex)));
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_FILE, MODE_PRIVATE);
    }

    private void toggleSection() {
        setSectionCollapsed(section.getVisibility() == View.VISIBLE);
    }

    private void setSectionCollapsed(boolean collapsed) {
        applySectionCollapsed(collapsed);
        prefs().edit().putBoolean(PREF_SECTION_COLLAPSED, collapsed).apply();
    }

    private void applySectionCollapsed(boolean collapsed) {
        section.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        sectionToggle.setText(collapsed ? R.string.show_section : R.string.hide_section);
    }

    private void sortCards() {
        cards.sort(Card.BY_EXPIRY);
        adapter.notifyDataSetChanged();
    }

    private void updateSectionHeader() {
        sectionCount.setText(plural(R.plurals.cards_count, cards.size()));
    }

    private void setupWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(USER_AGENT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
            }
        });
        web.loadUrl(SITE_URL);
    }

    private void openShop() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SHOP_URL)));
        } catch (ActivityNotFoundException ex) {
            toast(getString(R.string.no_browser));
        }
    }

    private void openAccessSettings() {
        Uri app = Uri.fromParts(PACKAGE_SCHEME, getPackageName(), null);
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app));
        } catch (ActivityNotFoundException ex) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void refreshPdfs() {
        boolean granted = Environment.isExternalStorageManager();
        btnAccess.setVisibility(granted ? View.GONE : View.VISIBLE);
        if (granted) {
            scanPdfs();
        }
    }

    private void scanPdfs() {
        if (scanning) {
            return;
        }
        scanning = true;
        handler.postDelayed(scanNoticeRunnable, SCAN_NOTICE_DELAY_MS);
        executor.execute(() -> {
            try {
                List<Card> found = library.scan();
                handler.post(() -> onPdfsScanned(found));
            } catch (IOException | RuntimeException ex) {
                handler.post(() -> onScanFailed(ex));
            }
        });
    }

    private void showScanNotice() {
        if (phase == Phase.IDLE) {
            bannerBeforeScan = banner.getText();
            banner.setText(R.string.scanning);
        }
    }

    private void onPdfsScanned(List<Card> found) {
        CharSequence previous = endScan();
        int start = cards.size();
        LocalDate today = LocalDate.now();
        for (Card card : found) {
            if (!card.isExpired(today) && !contains(card.number)) {
                cards.add(card);
            }
        }
        int added = cards.size() - start;
        if (added > 0) {
            sortCards();
            updateSectionHeader();
            persist();
            report(plural(R.plurals.cards_found, added));
        } else if (previous != null && getString(R.string.scanning).contentEquals(banner.getText())) {
            banner.setText(previous);
        }
    }

    private void onScanFailed(Exception ex) {
        endScan();
        report(getString(R.string.scan_failed, message(ex)));
    }

    private CharSequence endScan() {
        scanning = false;
        handler.removeCallbacks(scanNoticeRunnable);
        CharSequence previous = bannerBeforeScan;
        bannerBeforeScan = null;
        return previous;
    }

    private boolean contains(String number) {
        for (Card card : cards) {
            if (card.number != null && card.number.equals(number)) {
                return true;
            }
        }
        return false;
    }

    private void startCheck() {
        if (phase != Phase.IDLE) {
            toast(getString(R.string.check_running));
            return;
        }
        if (deleting) {
            toast(getString(R.string.delete_running));
            return;
        }
        if (!pageReady) {
            toast(getString(R.string.page_not_ready));
            return;
        }
        queue.clear();
        for (Card card : cards) {
            if (card.selected) {
                card.status = Card.Status.PENDING;
                card.balance = null;
                card.balanceValidity = null;
                card.error = null;
                queue.add(card);
            }
        }
        if (queue.isEmpty()) {
            toast(getString(R.string.no_selection));
            return;
        }
        adapter.notifyDataSetChanged();
        btnCheck.setEnabled(false);
        setSectionCollapsed(true);
        queueIndex = -1;
        advance();
    }

    private void advance() {
        queueIndex++;
        if (queueIndex >= queue.size()) {
            phase = Phase.IDLE;
            btnCheck.setEnabled(true);
            banner.setText(plural(R.plurals.cards_checked, queue.size()));
            return;
        }
        Card card = queue.get(queueIndex);
        card.status = Card.Status.CHECKING;
        adapter.notifyDataSetChanged();
        phase = Phase.PREP;
        captchaTriggered = false;
        captchaRetry = false;
        banner.setText(step(R.string.card_step_prep));
        scheduleTick();
    }

    private String step(int resId) {
        return getString(resId, queueIndex + 1, queue.size());
    }

    private void scheduleTick() {
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, POLL_MS);
    }

    private void tick() {
        web.evaluateJavascript(STATUS_JS, value -> {
            JSONObject status = decode(value);
            handleStatus(status);
            if (phase != Phase.IDLE) {
                scheduleTick();
            }
        });
    }

    private void handleStatus(JSONObject status) {
        if (status == null) {
            return;
        }
        if (queueIndex < 0 || queueIndex >= queue.size()) {
            return;
        }
        Card card = queue.get(queueIndex);
        boolean form = status.optBoolean("form", false);
        boolean result = status.optBoolean("result", false);
        boolean error = status.optBoolean("error", false);
        boolean filled = card.number.equals(status.optString("num")) && card.code.equals(status.optString("code"));

        switch (phase) {
            case PREP:
                if (result || error) {
                    web.evaluateJavascript(ANOTHER_JS, null);
                } else if (form) {
                    requestCaptcha(card);
                }
                break;
            case WAIT_TOKEN:
                if (!form) {
                    phase = Phase.PREP;
                } else if (!filled) {
                    web.evaluateJavascript(fillJs(card, false), null);
                } else if (status.optInt("token", 0) > 0) {
                    web.evaluateJavascript(SUBMIT_JS, null);
                    phase = Phase.WAIT_RESULT;
                    banner.setText(step(R.string.card_step_checking));
                } else if (!captchaTriggered) {
                    web.evaluateJavascript(EXECUTE_JS, null);
                    captchaTriggered = true;
                }
                break;
            case WAIT_RESULT:
                handleResult(card, status, form, result, error, filled);
                break;
            default:
                break;
        }
    }

    private void handleResult(Card card, JSONObject status, boolean form, boolean result, boolean error,
            boolean filled) {
        String invalid = status.optString("invalid");
        if (result) {
            String solde = status.optString("solde");
            if (card.number.equals(status.optString("resultNum")) && !solde.isEmpty()) {
                String date = status.optString("date");
                card.balance = solde;
                card.balanceValidity = date.isEmpty() ? null : date;
                card.status = Card.Status.DONE;
                card.selected = card.isEmptyBalance();
                sortCards();
                persist();
                advance();
            } else {
                phase = Phase.PREP;
            }
        } else if (error) {
            String message = status.optString("message");
            if (CAPTCHA_ERROR.matcher(message).find()) {
                captchaRetry = true;
                phase = Phase.PREP;
            } else {
                fail(card, message);
            }
        } else if (form && (status.optBoolean("captchaRejected", false) || (!invalid.isEmpty() && !filled))) {
            captchaRetry = true;
            requestCaptcha(card);
        } else if (form && !invalid.isEmpty()) {
            fail(card, invalid);
        }
    }

    private void requestCaptcha(Card card) {
        web.evaluateJavascript(fillJs(card, true), null);
        phase = Phase.WAIT_TOKEN;
        captchaTriggered = false;
        banner.setText(step(captchaRetry ? R.string.card_step_invalid_captcha : R.string.card_step_captcha));
    }

    private void fail(Card card, String message) {
        card.status = Card.Status.ERROR;
        card.error = message.isEmpty() ? null : message;
        refresh(card);
        persist();
        advance();
    }

    private void refresh(Card card) {
        int position = cards.indexOf(card);
        if (position >= 0) {
            adapter.notifyItemChanged(position);
        }
    }

    private String fillJs(Card card, boolean resetCaptcha) {
        return "(function(){function set(el,v){var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "s.call(el,v);el.setCustomValidity('');"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "el.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true}));}"
                + "var n=document.querySelector('#it_num_carte'),c=document.querySelector('#it_code_secret');"
                + "if(!n||!c)return 'NO_FORM';set(n,'" + card.number + "');set(c,'" + card.code + "');"
                + (resetCaptcha ? "try{if(window.hcaptcha)window.hcaptcha.reset();}catch(e){}" : "")
                + "var cap=document.querySelector('.h-captcha')||document.querySelector('iframe[src*=\"hcaptcha\"]')"
                + "||document.querySelector('#consultingForm');"
                + "if(cap)cap.scrollIntoView({block:'center',behavior:'smooth'});return 'OK';})()";
    }

    private JSONObject decode(String value) {
        try {
            Object outer = new JSONTokener(value).nextValue();
            String json = outer instanceof String ? (String) outer : value;
            return new JSONObject(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private void confirmDeleteEmpty() {
        List<Card> empties = new ArrayList<>();
        for (Card card : cards) {
            if (card.status == Card.Status.DONE && card.isEmptyBalance()) {
                empties.add(card);
            }
        }
        if (empties.isEmpty()) {
            toast(getString(R.string.no_empty_card));
            return;
        }
        confirmDelete(R.string.delete_empty, R.plurals.confirm_delete_empty, empties);
    }

    private void confirmDeleteSelection() {
        List<Card> selected = new ArrayList<>();
        for (Card card : cards) {
            if (card.selected) {
                selected.add(card);
            }
        }
        if (selected.isEmpty()) {
            toast(getString(R.string.no_selection));
            return;
        }
        confirmDelete(R.string.delete_selection, R.plurals.confirm_delete_selection, selected);
    }

    private void confirmDelete(int titleId, int messageId, List<Card> targets) {
        if (refuseWhileBusy()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(titleId)
                .setMessage(plural(messageId, targets.size()))
                .setPositiveButton(R.string.delete, (d, w) -> deleteCards(targets))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private boolean refuseWhileBusy() {
        if (phase != Phase.IDLE) {
            toast(getString(R.string.check_running));
            return true;
        }
        if (deleting) {
            toast(getString(R.string.delete_running));
            return true;
        }
        return false;
    }

    private void deleteCards(List<Card> targets) {
        if (refuseWhileBusy()) {
            return;
        }
        deleting = true;
        Set<String> numbers = new HashSet<>();
        for (Card card : targets) {
            numbers.add(card.number);
        }
        banner.setText(R.string.deleting);
        executor.execute(() -> {
            try {
                Set<String> deleted = library.delete(numbers);
                handler.post(() -> onCardsDeleted(numbers.size(), deleted));
            } catch (IOException | RuntimeException ex) {
                handler.post(() -> onDeleteFailed(ex));
            }
        });
    }

    private void onCardsDeleted(int requested, Set<String> deleted) {
        deleting = false;
        for (int i = cards.size() - 1; i >= 0; i--) {
            if (deleted.contains(cards.get(i).number)) {
                cards.remove(i);
                adapter.notifyItemRemoved(i);
            }
        }
        updateSectionHeader();
        persist();
        int kept = requested - deleted.size();
        if (kept == 0) {
            banner.setText(plural(R.plurals.cards_deleted, deleted.size()));
        } else if (deleted.isEmpty()) {
            banner.setText(plural(R.plurals.cards_not_deleted, kept));
        } else {
            banner.setText(getString(R.string.two_sentences, plural(R.plurals.cards_deleted, deleted.size()),
                    plural(R.plurals.cards_not_deleted, kept)));
        }
    }

    private void onDeleteFailed(Exception ex) {
        deleting = false;
        banner.setText(getString(R.string.delete_failed, message(ex)));
    }

    private void showPdf(Card card) {
        int width = getResources().getDisplayMetrics().widthPixels * 2;
        executor.execute(() -> {
            try {
                Uri uri = library.find(card.number);
                if (uri == null) {
                    throw new FileNotFoundException(card.number);
                }
                Bitmap bitmap = renderFirstPage(uri, width);
                handler.post(() -> showBarcodeDialog(bitmap));
            } catch (FileNotFoundException ex) {
                handler.post(() -> toast(getString(R.string.pdf_missing)));
            } catch (IOException | RuntimeException ex) {
                handler.post(() -> toast(getString(R.string.pdf_display_failed, message(ex))));
            }
        });
    }

    private Bitmap renderFirstPage(Uri uri, int width) throws IOException {
        try (ParcelFileDescriptor pfd = PdfParser.openReadOnly(this, uri);
             PdfRenderer renderer = new PdfRenderer(pfd);
             PdfRenderer.Page page = renderer.openPage(0)) {
            int height = (int) ((long) width * page.getHeight() / page.getWidth());
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        }
    }

    private void showBarcodeDialog(Bitmap bitmap) {
        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.WHITE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.addView(image, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(scroll);
        View.OnClickListener dismiss = v -> dialog.dismiss();
        scroll.setOnClickListener(dismiss);
        image.setOnClickListener(dismiss);
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.screenBrightness = 1.0f;
            window.setAttributes(params);
        }
        dialog.show();
    }

    private void report(CharSequence text) {
        if (phase == Phase.IDLE) {
            banner.setText(text);
        } else {
            toast(text);
        }
    }

    private String plural(int resId, int count) {
        return getResources().getQuantityString(resId, count, count);
    }

    private static String message(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private void toast(CharSequence message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
