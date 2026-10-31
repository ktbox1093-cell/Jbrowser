
import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class JBrowser extends MIDlet implements CommandListener {
    static final int M_URL = 0, M_INPUT = 1, M_SERVER = 2;
    static final int[] ZOOMS = {360, 480, 640, 800};

    Display display;
    View view;
    String server = "http://192.168.1.10:8080";
    String home = "https://duckduckgo.com/";
    int zoomIdx = 1;

    Command cmdOk = new Command("OK", Command.OK, 1);
    Command cmdCancel = new Command("Cancelar", Command.CANCEL, 2);
    Command cmdBack = new Command("Volver", Command.BACK, 1);
    TextBox box;
    int boxMode;
    String boxInputId;
    List menu;
    List vlist;
    String pendingVideo;

    static final String[] MENU = {
        "Ir a URL / buscar", "Atras", "Recargar", "Inicio",
        "Letra mas grande", "Mas contenido", "Servidor", "Salir"
    };

    public void startApp() {
        if (view != null) {
            display.setCurrent(view);
            return;
        }
        display = Display.getDisplay(this);
        boolean hasServer = loadSettings();
        view = new View(this);
        display.setCurrent(view);
        if (hasServer) {
            view.go(home);
        } else {
            showBox("Servidor (http://IP:8080)", server, M_SERVER);
        }
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
    }

    int zoomWidth() {
        return ZOOMS[zoomIdx];
    }

    boolean loadSettings() {
        boolean found = false;
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("jb", true);
            if (rs.getNumRecords() >= 1) {
                String s = new String(rs.getRecord(1), "UTF-8");
                int p = s.indexOf('\n');
                if (p > 0) {
                    server = s.substring(0, p);
                    try {
                        zoomIdx = Integer.parseInt(s.substring(p + 1).trim());
                    } catch (Exception e) {
                    }
                    if (zoomIdx < 0 || zoomIdx >= ZOOMS.length) zoomIdx = 1;
                    found = true;
                }
            }
        } catch (Exception e) {
        } finally {
            try {
                if (rs != null) rs.closeRecordStore();
            } catch (Exception e) {
            }
        }
        return found;
    }

    void saveSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("jb", true);
            byte[] b = (server + "\n" + zoomIdx).getBytes("UTF-8");
            if (rs.getNumRecords() >= 1) rs.setRecord(1, b, 0, b.length);
            else rs.addRecord(b, 0, b.length);
        } catch (Exception e) {
        } finally {
            try {
                if (rs != null) rs.closeRecordStore();
            } catch (Exception e) {
            }
        }
    }

    void showBox(String title, String init, int mode) {
        boxMode = mode;
        box = new TextBox(title, init, 512, TextField.ANY);
        box.addCommand(cmdOk);
        box.addCommand(cmdCancel);
        box.setCommandListener(this);
        display.setCurrent(box);
    }

    void showInput(String label, String id) {
        boxInputId = id;
        showBox(label.length() > 0 ? label : "Texto", "", M_INPUT);
    }

    void showMenu() {
        menu = new List("JBrowser", List.IMPLICIT);
        for (int i = 0; i < MENU.length; i++) menu.append(MENU[i], null);
        menu.addCommand(cmdBack);
        menu.setCommandListener(this);
        display.setCurrent(menu);
    }

    void showVideoChoice(String href) {
        pendingVideo = href;
        vlist = new List("Video", List.IMPLICIT);
        vlist.append("Reproducir (reproductor)", null);
        vlist.append("Abrir pagina", null);
        vlist.addCommand(cmdBack);
        vlist.setCommandListener(this);
        display.setCurrent(vlist);
    }

    public void commandAction(Command c, Displayable d) {
        if (d == menu) {
            if (c == List.SELECT_COMMAND) {
                int i = menu.getSelectedIndex();
                display.setCurrent(view);
                doMenu(i);
            } else {
                display.setCurrent(view);
            }
        } else if (d == vlist) {
            display.setCurrent(view);
            if (c == List.SELECT_COMMAND) {
                if (vlist.getSelectedIndex() == 0) playVideo(pendingVideo);
                else view.go(pendingVideo);
            }
        } else if (d == box) {
            if (c == cmdOk) {
                String t = box.getString().trim();
                display.setCurrent(view);
                handleBox(t);
            } else {
                display.setCurrent(view);
            }
        }
    }

    void doMenu(int i) {
        switch (i) {
            case 0:
                showBox("URL o busqueda", "", M_URL);
                break;
            case 1:
                view.back();
                break;
            case 2:
                view.reload();
                break;
            case 3:
                view.go(home);
                break;
            case 4:
                if (zoomIdx > 0) {
                    zoomIdx--;
                    saveSettings();
                    view.zoom();
                }
                break;
            case 5:
                if (zoomIdx < ZOOMS.length - 1) {
                    zoomIdx++;
                    saveSettings();
                    view.zoom();
                }
                break;
            case 6:
                showBox("Servidor (http://IP:8080)", server, M_SERVER);
                break;
            case 7:
                destroyApp(true);
                notifyDestroyed();
                break;
        }
    }

    void handleBox(String t) {
        if (t.length() == 0) return;
        if (boxMode == M_URL) {
            if (t.indexOf(' ') >= 0 || t.indexOf('.') < 0) {
                view.go("https://duckduckgo.com/?q=" + enc(t));
            } else {
                view.go(t);
            }
        } else if (boxMode == M_INPUT) {
            view.type(boxInputId, t);
        } else if (boxMode == M_SERVER) {
            while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
            if (t.indexOf("://") < 0) t = "http://" + t;
            server = t;
            saveSettings();
            view.sid = "";
            view.go(home);
        }
    }

    void playVideo(String href) {
        try {
            platformRequest(server + "/video?u=" + enc(href));
        } catch (Exception e) {
            view.status = "No se pudo abrir el reproductor";
            view.repaint();
        }
    }

    byte[] http(String url) throws IOException {
        HttpConnection c = null;
        InputStream in = null;
        try {
            c = (HttpConnection) Connector.open(url, Connector.READ, true);
            c.setRequestMethod(HttpConnection.GET);
            c.setRequestProperty("User-Agent", "JBrowser/1.0");
            int rc = c.getResponseCode();
            if (rc != HttpConnection.HTTP_OK) throw new IOException("HTTP " + rc);
            in = c.openInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception e) {
            }
            try {
                if (c != null) c.close();
            } catch (Exception e) {
            }
        }
    }

    static String enc(String s) {
        byte[] b;
        try {
            b = s.getBytes("UTF-8");
        } catch (Exception e) {
            b = s.getBytes();
        }
        StringBuffer sb = new StringBuffer();
        String hex = "0123456789ABCDEF";
        for (int i = 0; i < b.length; i++) {
            int ch = b[i] & 0xFF;
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append((char) ch);
            } else {
                sb.append('%').append(hex.charAt(ch >> 4)).append(hex.charAt(ch & 15));
            }
        }
        return sb.toString();
    }
}

class Elem {
    char type;
    int x, y, w, h;
    String id, label, href;
}

class View extends Canvas {
    static final int TH = 240;
    final JBrowser m;
    final Font font = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

    String sid = "";
    String title = "", url = "";
    String status = null;
    volatile boolean loading = false;
    int pageH = 0, scrollY = 0, sel = -1, ver = 0;
    Vector elems = new Vector();
    Hashtable tiles = new Hashtable();
    Hashtable pending = new Hashtable();
    Hashtable failed = new Hashtable();

    View(JBrowser m) {
        this.m = m;
        setFullScreenMode(true);
    }

    String path(String cmd, String extra) {
        return "/" + cmd + "?s=" + JBrowser.enc(sid) + "&w=" + m.zoomWidth() + extra
                + "&t=" + System.currentTimeMillis();
    }

    void go(String u) {
        load(path("go", "&u=" + JBrowser.enc(u)));
    }

    void back() {
        load(path("back", ""));
    }

    void reload() {
        load(path("reload", ""));
    }

    void zoom() {
        load(path("zoom", ""));
    }

    void type(String id, String text) {
        load(path("type", "&i=" + id + "&t=" + JBrowser.enc(text)));
    }

    void load(final String p) {
        if (loading) return;
        loading = true;
        status = null;
        repaint();
        new Thread(new Runnable() {
            public void run() {
                doLoad(p);
            }
        }).start();
    }

    void doLoad(String p) {
        try {
            byte[] b = m.http(m.server + p);
            String s = new String(b, "UTF-8");
            Vector lines = splitChar(s, '\n');
            String first = (String) lines.elementAt(0);
            if (!first.startsWith("OK")) {
                status = first.length() > 4 ? first.substring(4) : "Error";
            } else {
                String nsid = (String) lines.elementAt(1);
                int h = Integer.parseInt(((String) lines.elementAt(2)).trim());
                String ntitle = (String) lines.elementAt(3);
                String nurl = (String) lines.elementAt(4);
                Vector ne = new Vector();
                for (int i = 5; i < lines.size(); i++) {
                    String[] f = splitTab((String) lines.elementAt(i));
                    if (f.length < 7) continue;
                    Elem e = new Elem();
                    e.type = f[0].charAt(0);
                    e.x = Integer.parseInt(f[1]);
                    e.y = Integer.parseInt(f[2]);
                    e.w = Integer.parseInt(f[3]);
                    e.h = Integer.parseInt(f[4]);
                    e.id = f[5];
                    e.label = f[6];
                    e.href = f.length > 7 ? f[7] : "";
                    ne.addElement(e);
                }
                sid = nsid;
                pageH = h;
                title = ntitle;
                url = nurl;
                elems = ne;
                ver++;
                tiles.clear();
                pending.clear();
                failed.clear();
                scrollY = 0;
                sel = -1;
                pickVisible();
            }
        } catch (Throwable t) {
            String msg = t.getMessage();
            status = "Error: " + (msg == null ? t.toString() : msg);
        }
        loading = false;
        repaint();
    }

    void need(int i) {
        final Integer key = new Integer(i);
        if (i < 0 || i * TH >= pageH) return;
        if (tiles.containsKey(key) || pending.containsKey(key) || failed.containsKey(key)) return;
        pending.put(key, key);
        final int v = ver;
        final String s = sid;
        final int idx = i;
        new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] b = m.http(m.server + "/tile?s=" + JBrowser.enc(s) + "&i=" + idx + "&v=" + v);
                    Image im = Image.createImage(b, 0, b.length);
                    if (v == ver) {
                        tiles.put(key, im);
                        trim();
                    }
                } catch (Throwable t) {
                    failed.put(key, key);
                }
                pending.remove(key);
                repaint();
            }
        }).start();
    }

    void trim() {
        if (tiles.size() <= 8) return;
        int cur = scrollY / TH;
        Vector del = new Vector();
        Enumeration en = tiles.keys();
        while (en.hasMoreElements()) {
            Integer k = (Integer) en.nextElement();
            if (Math.abs(k.intValue() - cur) > 2) del.addElement(k);
        }
        for (int i = 0; i < del.size(); i++) tiles.remove(del.elementAt(i));
    }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);
        int first = scrollY / TH;
        int last = (scrollY + h - 1) / TH;
        for (int i = first; i <= last; i++) {
            Image im = (Image) tiles.get(new Integer(i));
            int ty = i * TH - scrollY;
            if (im != null) {
                g.drawImage(im, 0, ty, Graphics.TOP | Graphics.LEFT);
            } else if (i * TH < pageH) {
                g.setColor(0xE8E8E8);
                g.fillRect(0, ty, w, TH);
                need(i);
            }
        }
        need(last + 1);
        if (sel >= 0 && sel < elems.size()) {
            Elem e = (Elem) elems.elementAt(sel);
            int ey = e.y - scrollY;
            g.setColor(0xFF0000);
            g.drawRect(e.x - 2, ey - 2, e.w + 3, e.h + 3);
            g.drawRect(e.x - 1, ey - 1, e.w + 1, e.h + 1);
        }
        if (pageH > h) {
            int bh = Math.max(8, h * h / pageH);
            int by = (h - bh) * scrollY / Math.max(1, pageH - h);
            g.setColor(0x808080);
            g.fillRect(w - 3, by, 3, bh);
        }
        String msg = null;
        if (loading) msg = "Cargando...";
        else if (status != null) msg = status;
        else if (sel >= 0 && sel < elems.size()) {
            Elem e = (Elem) elems.elementAt(sel);
            msg = e.label.length() > 0 ? e.label : e.href;
        }
        if (msg != null && msg.length() > 0) {
            int fh = font.getHeight() + 2;
            g.setColor(0x000000);
            g.fillRect(0, h - fh, w, fh);
            g.setColor(0xFFFFFF);
            g.setFont(font);
            g.drawString(msg, 2, h - fh + 1, Graphics.TOP | Graphics.LEFT);
        }
    }

    protected void keyPressed(int k) {
        status = null;
        failed.clear();
        int ga = 0;
        try {
            ga = getGameAction(k);
        } catch (Exception e) {
        }
        if (k == -6 || k == 'm' || k == 'M' || k == KEY_NUM0) {
            m.showMenu();
        } else if (k == -7 || k == -8 || k == 'b' || k == 'B') {
            back();
        } else if (k == KEY_STAR || k == 'g' || k == 'G') {
            m.showBox("URL o busqueda", "", JBrowser.M_URL);
        } else if (k == 'r' || k == 'R') {
            reload();
        } else if (k == -5 || ga == FIRE || k == KEY_NUM5) {
            activate();
        } else if (ga == UP) {
            scrollBy(-80);
        } else if (ga == DOWN) {
            scrollBy(80);
        } else if (ga == LEFT) {
            moveSel(-1);
        } else if (ga == RIGHT) {
            moveSel(1);
        } else if (k == KEY_NUM2) {
            scrollBy(-(getHeight() - 20));
        } else if (k == KEY_NUM8) {
            scrollBy(getHeight() - 20);
        } else if (k == KEY_NUM1) {
            scrollTo(0);
        } else if (k == KEY_NUM7) {
            scrollTo(pageH);
        }
        repaint();
    }

    protected void keyRepeated(int k) {
        int ga = 0;
        try {
            ga = getGameAction(k);
        } catch (Exception e) {
        }
        if (ga == UP || ga == DOWN) {
            keyPressed(k);
        }
    }

    int maxScroll() {
        return Math.max(0, pageH - getHeight());
    }

    void scrollTo(int y) {
        scrollY = Math.max(0, Math.min(y, maxScroll()));
        pickVisible();
    }

    void scrollBy(int d) {
        scrollTo(scrollY + d);
    }

    boolean visible(Elem e) {
        return e.y + e.h > scrollY && e.y < scrollY + getHeight();
    }

    void pickVisible() {
        if (sel >= 0 && sel < elems.size() && visible((Elem) elems.elementAt(sel))) return;
        sel = -1;
        for (int i = 0; i < elems.size(); i++) {
            Elem e = (Elem) elems.elementAt(i);
            if (e.y >= scrollY && e.y < scrollY + getHeight() - 14) {
                sel = i;
                break;
            }
        }
    }

    void moveSel(int d) {
        if (elems.size() == 0) return;
        int n = sel < 0 ? (d > 0 ? 0 : elems.size() - 1) : sel + d;
        if (n < 0 || n >= elems.size()) return;
        sel = n;
        Elem e = (Elem) elems.elementAt(sel);
        int h = getHeight();
        if (e.y < scrollY) scrollY = Math.max(0, e.y - 20);
        else if (e.y + e.h > scrollY + h - 16) scrollY = Math.min(maxScroll(), e.y + e.h - h + 36);
    }

    void activate() {
        if (sel < 0 || sel >= elems.size()) return;
        Elem e = (Elem) elems.elementAt(sel);
        switch (e.type) {
            case 'L':
                go(e.href);
                break;
            case 'V':
                m.showVideoChoice(e.href);
                break;
            case 'I':
                m.showInput(e.label, e.id);
                break;
            default:
                load(path("click", "&i=" + e.id));
        }
    }

    static Vector splitChar(String s, char c) {
        Vector v = new Vector();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                v.addElement(s.substring(start, i));
                start = i + 1;
            }
        }
        v.addElement(s.substring(start));
        return v;
    }

    static String[] splitTab(String s) {
        Vector v = splitChar(s, '\t');
        String[] r = new String[v.size()];
        v.copyInto(r);
        return r;
    }
}
