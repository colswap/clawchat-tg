package com.clawchat.render;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Standalone manual-QA activity. Renders hardcoded sample messages covering
 * every block type supported by the render layer.
 *
 * To launch manually, add to AndroidManifest.xml:
 *   <activity android:name="com.clawchat.render.ClawRenderPreviewActivity"
 *             android:exported="true">
 *     <intent-filter>
 *       <action android:name="android.intent.action.MAIN"/>
 *       <category android:name="android.intent.category.LAUNCHER"/>
 *     </intent-filter>
 *   </activity>
 */
public class ClawRenderPreviewActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF11111B);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        addSample(root, "1. Pure markdown",
                "# Heading 1\n"
                        + "## Heading 2\n"
                        + "Some **bold**, *italic*, ~~strike~~ and `inline code`.\n\n"
                        + "- bullet one\n- bullet two\n- bullet three\n\n"
                        + "| Col A | Col B |\n|---|---|\n| 1 | 2 |\n| foo | bar |\n\n"
                        + "한글 줄간격 테스트입니다. CJK line spacing should be comfortable.\n");

        addSample(root, "2. Multi-language code blocks",
                "Here is some Java:\n"
                        + "```java\n"
                        + "public class Hello {\n"
                        + "    public static void main(String[] args) {\n"
                        + "        System.out.println(\"hi\");\n"
                        + "    }\n"
                        + "}\n"
                        + "```\n\n"
                        + "And Python:\n"
                        + "```python\n"
                        + "def greet(name):\n"
                        + "    print(f'hello {name}')\n"
                        + "```\n\n"
                        + "And bash:\n"
                        + "```bash\n"
                        + "echo 'hello world' | grep hello\n"
                        + "```\n");

        addSample(root, "3. HTML artifact",
                "Look at this HTML:\n"
                        + "```html\n"
                        + "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#eef;padding:20px;'>"
                        + "<h2>Hello from an artifact</h2>"
                        + "<p>This is a live HTML preview.</p>"
                        + "<button onclick=\"this.innerText='clicked!'\">Click me</button>"
                        + "</body></html>\n"
                        + "```\n");

        addSample(root, "4. Mermaid artifact",
                "Flowchart:\n"
                        + "```mermaid\n"
                        + "graph TD\n"
                        + "  A[Start] --> B{Is it working?}\n"
                        + "  B -- Yes --> C[Great]\n"
                        + "  B -- No --> D[Debug]\n"
                        + "  D --> B\n"
                        + "```\n");

        addSample(root, "5. Mixed content + SVG",
                "Mixed message with intro text, an **SVG artifact**, and a trailing code block.\n\n"
                        + "```svg\n"
                        + "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 100'>"
                        + "<rect width='200' height='100' fill='#89b4fa'/>"
                        + "<circle cx='100' cy='50' r='30' fill='#f38ba8'/>"
                        + "<text x='100' y='55' text-anchor='middle' fill='white' font-family='sans-serif'>SVG</text>"
                        + "</svg>\n"
                        + "```\n\n"
                        + "And finally a JS snippet:\n"
                        + "```js\n"
                        + "const x = [1,2,3].map(n => n * 2);\n"
                        + "console.log(x);\n"
                        + "```\n");

        setContentView(scroll);
    }

    private void addSample(LinearLayout parent, String title, String body) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(Color.WHITE);
        header.setTextSize(16);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(16);
        hlp.bottomMargin = dp(6);
        parent.addView(header, hlp);

        ClawMessageOverlay overlay = new ClawMessageOverlay(this);
        overlay.setBackgroundColor(0xFF1E1E2E);
        int p = dp(8);
        overlay.setPadding(p, p, p, p);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        parent.addView(overlay, olp);
        overlay.setMessage(body);
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
