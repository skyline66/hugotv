package cn.hugotv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * HugoTV 桌面启动器
 *
 * 面向 Android 电视/盒子的轻量中文桌面：
 *  - 大时钟 + 中文日期（星期）
 *  - 应用卡片网格（遥控器方向键 + 确认键）
 *  - 多种渐变主题、可调列数
 *  - 无广告、无网络、无后台服务，完全本地
 */
public class MainActivity extends Activity {

    private static final String PREFS = "hugotv_prefs";
    private static final String KEY_THEME = "theme";   // 0..3
    private static final String KEY_COLS = "cols";     // 4..7

    // 主题: [背景上, 背景中, 背景下, 强调色]
    private static final int[][] THEMES = {
            {0xFF0F2027, 0xFF203A43, 0xFF2C5364, 0xFF4FC3F7}, // 深蓝
            {0xFF061A16, 0xFF0F2E1D, 0xFF1B4A2A, 0xFF66BB6A}, // 墨绿
            {0xFF200A0C, 0xFF3A1215, 0xFF572027, 0xFFEF6C6C}, // 绯红
            {0xFF2E3440, 0xFF3B4252, 0xFF4C566A, 0xFF81A1C1}, // 晨雾
    };
    private static final String[] THEME_NAMES = {"深蓝", "墨绿", "绯红", "晨雾"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000L);
        }
    };

    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView clockText;
    private TextView dateText;
    private GridView grid;
    private AppAdapter adapter;
    private final List<AppEntry> apps = new ArrayList<AppEntry>();
    private int accent = THEMES[0][3];

    private static final class AppEntry {
        final String label;
        final Drawable icon;
        final Intent intent;

        AppEntry(String label, Drawable icon, Intent intent) {
            this.label = label;
            this.icon = icon;
            this.intent = intent;
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        immersive();
        buildUi();
        loadApps();
        updateClock();
        handler.post(ticker);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            immersive();
        }
    }

    private void immersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    // ------------------------------------------------------------ UI

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        applyTheme();

        // 顶部：时钟 + 日期
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(dp(24), dp(26), dp(24), dp(10));

        clockText = new TextView(this);
        clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 82);
        clockText.setTextColor(Color.WHITE);
        clockText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        clockText.setShadowLayer(8f, 0f, 3f, Color.argb(110, 0, 0, 0));

        dateText = new TextView(this);
        dateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        dateText.setTextColor(Color.argb(215, 255, 255, 255));
        dateText.setShadowLayer(6f, 0f, 2f, Color.argb(90, 0, 0, 0));

        header.addView(clockText);
        header.addView(dateText);

        // 应用网格
        grid = new GridView(this);
        grid.setNumColumns(prefs.getInt(KEY_COLS, 5));
        grid.setHorizontalSpacing(dp(14));
        grid.setVerticalSpacing(dp(14));
        grid.setPadding(dp(28), dp(8), dp(28), dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(false);
        grid.setFocusable(true);
        grid.setDrawSelectorOnTop(false);

        // 底部：信息 + 设置
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(28), dp(4), dp(28), dp(18));

        TextView info = new TextView(this);
        info.setText("HugoTV · 轻量桌面 · 无广告 · 不联网");
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        info.setTextColor(Color.argb(150, 255, 255, 255));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

        TextView settings = new TextView(this);
        settings.setText("设 置");
        settings.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        settings.setTextColor(Color.WHITE);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(22), dp(9), dp(22), dp(9));
        settings.setBackground(makePill(accent));
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });

        bottom.addView(info);
        bottom.addView(spacer);
        bottom.addView(settings);

        root.addView(header);
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bottom);

        setContentView(root);
    }

    private void applyTheme() {
        int t = prefs.getInt(KEY_THEME, 0);
        if (t < 0 || t >= THEMES.length) {
            t = 0;
        }
        int[] c = THEMES[t];
        accent = c[3];
        if (root != null) {
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{c[0], c[1], c[2]});
            bg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            root.setBackgroundDrawable(bg);
        }
        if (grid != null && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat hh = new SimpleDateFormat("HH:mm", Locale.CHINA);
        SimpleDateFormat dd = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA);
        if (clockText != null) {
            clockText.setText(hh.format(cal.getTime()));
        }
        if (dateText != null) {
            dateText.setText(dd.format(cal.getTime()));
        }
    }

    // ------------------------------------------------------------ apps

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : resolved) {
            if (ri == null || ri.activityInfo == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) {
                continue; // 不显示自己
            }
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setClassName(pkg, ri.activityInfo.name);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String label = ri.loadLabel(pm).toString();
            Drawable icon;
            try {
                icon = ri.activityInfo.loadIcon(pm);
            } catch (Throwable th) {
                icon = getDrawable(android.R.drawable.sym_def_app_icon);
            }
            apps.add(new AppEntry(label, icon, launch));
        }
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return java.text.Collator.getInstance(Locale.CHINA).compare(a.label, b.label);
            }
        });

        if (adapter == null) {
            adapter = new AppAdapter();
            grid.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private final class AppAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppEntry entry = apps.get(position);
            CardHolder holder;
            if (convertView == null) {
                holder = new CardHolder();
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(10), dp(14), dp(10), dp(10));
                card.setFocusable(true);

                ImageView icon = new ImageView(MainActivity.this);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));

                TextView label = new TextView(MainActivity.this);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                label.setTextColor(Color.WHITE);
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                label.setPadding(0, dp(8), 0, 0);

                card.addView(icon);
                card.addView(label);
                card.setBackground(makeCardBg());
                holder.card = card;
                holder.icon = icon;
                holder.label = label;
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (CardHolder) convertView.getTag();
            }
            holder.icon.setImageDrawable(entry.icon);
            holder.label.setText(entry.label);
            holder.card.setOnClickListener(new LaunchListener(entry));
            return convertView;
        }
    }

    private static final class CardHolder {
        LinearLayout card;
        ImageView icon;
        TextView label;
    }

    private final class LaunchListener implements View.OnClickListener {
        private final AppEntry entry;

        LaunchListener(AppEntry entry) {
            this.entry = entry;
        }

        @Override
        public void onClick(View v) {
            try {
                startActivity(entry.intent);
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    // ------------------------------------------------------------ settings

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(10), dp(20), dp(12));

        TextView themeTitle = titleText("背景主题");
        panel.addView(themeTitle);

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < THEMES.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(THEME_NAMES[i]);
            b.setTextColor(Color.WHITE);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            b.setBackground(makePill(THEMES[i][3]));
            b.setPadding(dp(10), dp(6), dp(10), dp(6));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putInt(KEY_THEME, idx).apply();
                    applyTheme();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            themeRow.addView(b, lp);
        }
        panel.addView(themeRow);

        TextView colsTitle = titleText("每行应用数");
        panel.addView(colsTitle);

        LinearLayout colsRow = new LinearLayout(this);
        colsRow.setOrientation(LinearLayout.HORIZONTAL);
        colsRow.setGravity(Gravity.CENTER);
        int[] cols = {4, 5, 6, 7};
        for (final int c : cols) {
            Button b = new Button(this);
            b.setText(String.valueOf(c));
            b.setTextColor(Color.WHITE);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            b.setBackground(makePill(accent));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putInt(KEY_COLS, c).apply();
                    grid.setNumColumns(c);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            colsRow.addView(b, lp);
        }
        panel.addView(colsRow);

        new AlertDialog.Builder(this)
                .setTitle("桌面设置")
                .setView(panel)
                .setNegativeButton("关闭", null)
                .show();
    }

    private TextView titleText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    // ------------------------------------------------------------ drawables

    private Drawable makeCardBg() {
        GradientDrawable normal = rounded(Color.argb(24, 255, 255, 255),
                Color.argb(60, 255, 255, 255), 1, dp(16));
        GradientDrawable focused = rounded(Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent)),
                accent, 2, dp(16));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    private Drawable makePill(int color) {
        return rounded(Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)),
                color, 1, dp(22));
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(radius);
        g.setStroke(dp(strokeWidth), stroke);
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
