package com.eric.leclercsolde;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String SITE_URL = "https://www.e-cartecadeau.leclerc/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final long POLL_MS = 600L;
    private static final String STORE_FILE = "cards.json";

    private static final String STATUS_JS =
            "(function(){var t=document.body?document.body.innerText:'';"
                    + "var ta=document.querySelector('textarea[name=\"h-captcha-response\"]');"
                    + "var token=(ta&&ta.value)?ta.value.length:0;"
                    + "var hasForm=!!document.querySelector('#it_num_carte');"
                    + "var solde=null,date=null,err=false;"
                    + "var m=t.match(/Solde restant\\s*:?\\s*([\\d.,]+)\\s*\\u20ac/i);if(m)solde=m[1];"
                    + "var d=t.match(/utilisable jusqu['\\u2019]au\\s*([0-9\\/]+)/i);if(d)date=d[1];"
                    + "if(/Captcha est invalide/i.test(t))err=true;"
                    + "return JSON.stringify({hasForm:hasForm,token:token,solde:solde,date:date,err:err});})()";

    private static final String SUBMIT_JS =
            "(function(){var b=document.querySelector('.btn-consulting');if(b){b.click();return 'CLICK';}"
                    + "if(window.jQuery){window.jQuery('#consultingForm').request('onSubmitConsultingForm');return 'REQ';}"
                    + "return 'NO';})()";

    private static final String EXECUTE_JS =
            "(function(){try{window.hcaptcha.execute();return 'EXEC';}catch(e){return 'ERR:'+e.message;}})()";

    private static final String ANOTHER_JS =
            "(function(){var e=[].slice.call(document.querySelectorAll('button,a,.btn,input[type=button],input[type=submit]'));"
                    + "for(var i=0;i<e.length;i++){var s=(e[i].textContent||e[i].value||'');"
                    + "if(/consulter une autre carte/i.test(s)){e[i].click();return 'ANOTHER';}}return 'NONE';})()";

    private enum Phase { IDLE, PREP, WAIT_TOKEN, WAIT_RESULT }

    private final List<Card> cards = new ArrayList<>();
    private final List<Card> queue = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private CardAdapter adapter;
    private WebView web;
    private TextView banner;
    private Button btnCheck;

    private boolean pageReady = false;
    private Phase phase = Phase.IDLE;
    private int queueIndex = -1;
    private boolean captchaTriggered = false;

    private ActivityResultLauncher<String[]> picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        banner = findViewById(R.id.banner);
        RecyclerView list = findViewById(R.id.list);
        adapter = new CardAdapter(cards, this::showPdf);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        loadCards();

        web = findViewById(R.id.web);
        setupWebView();

        picker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::onPdfsPicked);

        Button btnImport = findViewById(R.id.btnImport);
        btnCheck = findViewById(R.id.btnCheck);
        Button btnDeleteEmpty = findViewById(R.id.btnDeleteEmpty);
        Button btnDeleteSelection = findViewById(R.id.btnDeleteSelection);

        btnImport.setOnClickListener(v -> picker.launch(new String[]{"application/pdf"}));
        btnCheck.setOnClickListener(v -> startCheck());
        btnDeleteEmpty.setOnClickListener(v -> confirmDeleteEmpty());
        btnDeleteSelection.setOnClickListener(v -> confirmDeleteSelection());
    }

    @Override
    protected void onPause() {
        super.onPause();
        persist();
    }

    private void loadCards() {
        try (InputStream in = openFileInput(STORE_FILE)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            JSONArray array = new JSONArray(bytes.toString("UTF-8"));
            for (int i = 0; i < array.length(); i++) {
                Card card = Card.fromJson(array.getJSONObject(i));
                if (card.status == Card.Status.CHECKING) {
                    card.status = Card.Status.PENDING;
                }
                cards.add(card);
            }
            adapter.notifyDataSetChanged();
            if (!cards.isEmpty()) {
                banner.setText(cards.size() + " carte(s) en mémoire.");
            }
        } catch (FileNotFoundException ignored) {
        } catch (Exception ex) {
            banner.setText("Lecture mémoire impossible : " + ex.getMessage());
        }
    }

    private void persist() {
        try {
            JSONArray array = new JSONArray();
            for (Card card : new ArrayList<>(cards)) {
                array.put(card.toJson());
            }
            byte[] data = array.toString().getBytes("UTF-8");
            try (FileOutputStream out = openFileOutput(STORE_FILE, MODE_PRIVATE)) {
                out.write(data);
            }
        } catch (Exception ignored) {
        }
    }

    private void setupWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
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

    private void onPdfsPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        banner.setText("Lecture de " + uris.size() + " PDF…");
        executor.execute(() -> {
            int added = 0;
            int skipped = 0;
            for (Uri uri : uris) {
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
                try {
                    Card card = PdfParser.parse(this, uri);
                    if (card != null && !contains(card.number)) {
                        cards.add(card);
                        added++;
                    } else if (card != null) {
                        skipped++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    skipped++;
                }
            }
            int finalAdded = added;
            int finalSkipped = skipped;
            handler.post(() -> {
                adapter.notifyDataSetChanged();
                banner.setText(finalAdded + " carte(s) ajoutée(s), " + finalSkipped + " ignorée(s).");
                persist();
            });
        });
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
            toast("Vérification déjà en cours.");
            return;
        }
        if (!pageReady) {
            toast("Page pas encore chargée, réessaie dans un instant.");
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
            toast("Aucune carte sélectionnée.");
            return;
        }
        adapter.notifyDataSetChanged();
        btnCheck.setEnabled(false);
        queueIndex = -1;
        advance();
    }

    private void advance() {
        queueIndex++;
        if (queueIndex >= queue.size()) {
            phase = Phase.IDLE;
            btnCheck.setEnabled(true);
            banner.setText("Terminé. " + queue.size() + " carte(s) vérifiée(s).");
            return;
        }
        Card card = queue.get(queueIndex);
        card.status = Card.Status.CHECKING;
        adapter.notifyDataSetChanged();
        phase = Phase.PREP;
        captchaTriggered = false;
        banner.setText("Carte " + (queueIndex + 1) + "/" + queue.size() + " : préparation…");
        handler.postDelayed(this::tick, POLL_MS);
    }

    private void tick() {
        web.evaluateJavascript(STATUS_JS, value -> {
            JSONObject status = decode(value);
            handleStatus(status);
            if (phase != Phase.IDLE) {
                handler.postDelayed(this::tick, POLL_MS);
            }
        });
    }

    private void handleStatus(JSONObject status) {
        if (status == null) {
            return;
        }
        boolean hasForm = status.optBoolean("hasForm", false);
        int token = status.optInt("token", 0);
        String solde = status.isNull("solde") ? null : status.optString("solde", null);
        String date = status.isNull("date") ? null : status.optString("date", null);
        boolean err = status.optBoolean("err", false);
        Card card = queue.get(queueIndex);

        switch (phase) {
            case PREP:
                if (solde != null && !hasForm) {
                    web.evaluateJavascript(ANOTHER_JS, null);
                } else if (hasForm) {
                    web.evaluateJavascript(fillJs(card), null);
                    phase = Phase.WAIT_TOKEN;
                    banner.setText("Carte " + (queueIndex + 1) + "/" + queue.size()
                            + " : résous le captcha dans la page.");
                }
                break;
            case WAIT_TOKEN:
                if (token > 0) {
                    web.evaluateJavascript(SUBMIT_JS, null);
                    phase = Phase.WAIT_RESULT;
                    banner.setText("Carte " + (queueIndex + 1) + "/" + queue.size() + " : vérification…");
                } else if (!captchaTriggered) {
                    web.evaluateJavascript(EXECUTE_JS, null);
                    captchaTriggered = true;
                }
                break;
            case WAIT_RESULT:
                if (solde != null) {
                    card.balance = solde;
                    card.balanceValidity = date;
                    card.status = Card.Status.DONE;
                    card.selected = card.isEmptyBalance();
                    adapter.notifyDataSetChanged();
                    persist();
                    advance();
                } else if (err) {
                    web.evaluateJavascript(fillJs(card), null);
                    phase = Phase.WAIT_TOKEN;
                    captchaTriggered = false;
                    banner.setText("Carte " + (queueIndex + 1) + "/" + queue.size()
                            + " : captcha invalide, recommence le captcha.");
                }
                break;
            default:
                break;
        }
    }

    private String fillJs(Card card) {
        return "(function(){function set(el,v){var p=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
                + "var s=Object.getOwnPropertyDescriptor(p,'value').set;s.call(el,v);"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "el.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true}));}"
                + "var n=document.querySelector('#it_num_carte'),c=document.querySelector('#it_code_secret');"
                + "if(!n||!c)return 'NO_FORM';set(n,'" + card.number + "');set(c,'" + card.code + "');"
                + "try{if(window.hcaptcha)window.hcaptcha.reset();}catch(e){}"
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
            toast("Aucune carte vérifiée à 0 €.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Supprimer les cartes à 0 €")
                .setMessage("Retirer " + empties.size() + " carte(s) à 0 € de la liste ?")
                .setPositiveButton("Supprimer", (d, w) -> {
                    cards.removeAll(empties);
                    adapter.notifyDataSetChanged();
                    persist();
                    banner.setText(empties.size() + " carte(s) à 0 € supprimée(s).");
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void confirmDeleteSelection() {
        List<Card> selected = new ArrayList<>();
        for (Card card : cards) {
            if (card.selected) {
                selected.add(card);
            }
        }
        if (selected.isEmpty()) {
            toast("Aucune carte sélectionnée.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Supprimer la sélection")
                .setMessage("Retirer " + selected.size() + " carte(s) sélectionnée(s) de la liste ?")
                .setPositiveButton("Supprimer", (d, w) -> {
                    cards.removeAll(selected);
                    adapter.notifyDataSetChanged();
                    persist();
                    banner.setText(selected.size() + " carte(s) supprimée(s).");
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showPdf(Card card) {
        if (card.sourceUri == null) {
            toast("PDF source indisponible.");
            return;
        }
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(Uri.parse(card.sourceUri), "r")) {
            if (pfd == null) {
                toast("PDF introuvable.");
                return;
            }
            try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                PdfRenderer.Page page = renderer.openPage(0);
                int width = getResources().getDisplayMetrics().widthPixels * 2;
                int height = (int) ((long) width * page.getHeight() / page.getWidth());
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                showBarcodeDialog(bitmap);
            }
        } catch (Exception ex) {
            toast("Erreur affichage PDF : " + ex.getMessage());
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
