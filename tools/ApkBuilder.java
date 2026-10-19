import com.reandroid.apk.ApkModule;
import com.reandroid.apk.xmlencoder.XMLEncodeSource;
import com.reandroid.archive.Archive;
import com.reandroid.archive.FileInputSource;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.coder.EncodeResult;
import com.reandroid.arsc.coder.ValueCoder;
import com.reandroid.arsc.value.Entry;
import com.reandroid.xml.source.XMLFileParserSource;

import java.io.File;

/** Assembles the unsigned APK: resource table, binary manifest, adaptive icon, fonts and dex. */
public class ApkBuilder {
    public static void main(String[] a) throws Exception {
        File app = new File(a[0]);
        File dex = new File(a[1]);
        File out = new File(a[2]); // a[3]: third-party licence text bundled into the APK

        ApkModule module = new ApkModule();
        TableBlock table = new TableBlock();
        module.setTableBlock(table);
        module.initializeAndroidFramework(35);
        PackageBlock pkg = table.newPackage(0x7f, "com.glyphcountdown.app");

        // adaptive icon pieces
        Entry bg = pkg.getOrCreate("", "color", "ic_launcher_bg");
        EncodeResult black = ValueCoder.encode("#FF000000");
        bg.setValueAsRaw(black.valueType, black.value);
        pkg.getOrCreate("-nodpi", "drawable", "ic_launcher_fg")
                .setValueAsString("res/drawable-nodpi-v4/ic_launcher_fg.png");
        pkg.getOrCreate("-xxxhdpi", "mipmap", "ic_launcher")
                .setValueAsString("res/mipmap-xxxhdpi-v4/ic_launcher.png");
        pkg.getOrCreate("-anydpi-v26", "mipmap", "ic_launcher")
                .setValueAsString("res/mipmap-anydpi-v26/ic_launcher.xml");

        module.add(new FileInputSource(new File(app, "res/drawable-nodpi/ic_launcher_fg.png"),
                "res/drawable-nodpi-v4/ic_launcher_fg.png"));
        module.add(new FileInputSource(new File(app, "res/mipmap-xxxhdpi/ic_launcher.png"),
                "res/mipmap-xxxhdpi-v4/ic_launcher.png"));
        XMLEncodeSource icon = new XMLEncodeSource(pkg, new XMLFileParserSource(
                "res/mipmap-anydpi-v26/ic_launcher.xml", new File(app, "res/mipmap-anydpi-v26/ic_launcher.xml")));
        module.add(icon);

        // ---- widgets: tap-area ids, preview images, descriptions, layouts, provider info
        for (String id : new String[]{"w_image_light", "w_image_dark", "w_toggle", "w_reset", "w_open", "w_open2", "w_open3", "w_open4"}) {
            pkg.getOrCreate("", "id", id).setValueAsBoolean(false);
        }
        String[][] widgets = {{"1", "1x1", "Timer dial with minutes left. Tap to start or pause."},
                {"2", "2x1", "Time left with a play/pause button."},
                {"4", "2x2", "Full tick ring with reset and play/pause."}};
        for (String[] wd : widgets) {
            for (String v : new String[]{"widget_prev_", "widget_prev_light_"}) {
                pkg.getOrCreate("-nodpi", "drawable", v + wd[0])
                        .setValueAsString("res/drawable-nodpi-v4/" + v + wd[0] + ".png");
                module.add(new FileInputSource(new File(app, "res/drawable-nodpi/" + v + wd[0] + ".png"),
                        "res/drawable-nodpi-v4/" + v + wd[0] + ".png"));
            }
            pkg.getOrCreate("", "string", "widget_desc_" + wd[0]).setValueAsString(wd[2]);
        }
        for (String[] wd : widgets) {
            pkg.getOrCreate("", "layout", "widget_" + wd[1]).setValueAsString("res/layout/widget_" + wd[1] + ".xml");
            // night variant: the launcher picks it when the phone is in dark appearance
            pkg.getOrCreate("-night", "layout", "widget_" + wd[1]).setValueAsString("res/layout-night-v8/widget_" + wd[1] + ".xml");
        }
        for (String[] wd : widgets) {
            pkg.getOrCreate("", "xml", "widget_info_" + wd[1]).setValueAsString("res/xml/widget_info_" + wd[1] + ".xml");
        }
        for (String[] wd : widgets) {
            String layout = "res/layout/widget_" + wd[1] + ".xml";
            module.add(new XMLEncodeSource(pkg, new XMLFileParserSource(layout, new File(app, layout))));
            module.add(new XMLEncodeSource(pkg, new XMLFileParserSource("res/layout-night-v8/widget_" + wd[1] + ".xml",
                    new File(app, "res/layout-night/widget_" + wd[1] + ".xml"))));
            String info = "res/xml/widget_info_" + wd[1] + ".xml";
            module.add(new XMLEncodeSource(pkg, new XMLFileParserSource(info, new File(app, info))));
        }

        table.setCurrentPackage(pkg);
        XMLEncodeSource manifest = new XMLEncodeSource(pkg, new XMLFileParserSource(
                "AndroidManifest.xml", new File(app, "AndroidManifest.xml")));
        manifest.setMethod(Archive.STORED);
        manifest.setSort(0);
        module.add(manifest);

        for (File f : new File(app, "assets/fonts").listFiles()) {
            module.add(new FileInputSource(f, "assets/fonts/" + f.getName()));
        }
        module.add(new FileInputSource(dex, "classes.dex"));
        module.add(new FileInputSource(new File(a[3]), "assets/LICENSES.txt"));

        module.writeApk(out);
        System.out.println("wrote " + out + " (" + out.length() + " bytes)");
    }
}
