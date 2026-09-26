package ir.dastyar.eghsat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.NumberFormat;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout list;
    TextView summary;
    Theme th;
    final int REQ = 90;

    int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    TextView tv(String s, float size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        return t;
    }

    GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radiusDp));
        return g;
    }

    GradientDrawable roundedStroke(int color, int strokeColor, float radiusDp, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1,dp(strokeDp)), strokeColor);
        return g;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        th = Theme.get(this);
        getWindow().setStatusBarColor(th.bg);
        build();
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ);
    }

    void build() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(th.bg);

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(12));

        // top bar: title + theme toggle
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = tv("دستیار اقساط", 24, th.text);
        title.setTypeface(null, 1);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView themeBtn = tv(th.dark ? "☀️" : "🌙", 18, th.text);
        themeBtn.setGravity(Gravity.CENTER);
        themeBtn.setBackground(rounded(th.card, 22));
        themeBtn.setOnClickListener(v -> { Theme.setDark(this, !th.dark); recreate(); });
        LinearLayout.LayoutParams themeLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        top.addView(themeBtn, themeLp);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(-1,-2);
        topLp.setMargins(0,0,0,dp(16));
        root.addView(top, topLp);

        // summary card
        summary = tv("", 15, th.primaryText);
        summary.setPadding(dp(16),dp(14),dp(16),dp(14));
        summary.setBackground(rounded(th.primary, 18));
        summary.setLineSpacing(dp(6),1f);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, -2);
        sLp.setMargins(0,0,0,dp(18));
        root.addView(summary, sLp);

        TextView h = tv("اقساط من", 18, th.text); h.setTypeface(null,1);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1,-2);
        hLp.setMargins(dp(2),0,dp(2),dp(8));
        root.addView(h, hLp);

        ScrollView sv = new ScrollView(this);
        sv.setClipToPadding(false);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0,0,0,dp(76));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1,0,1));

        outer.addView(root, new FrameLayout.LayoutParams(-1,-1));

        // floating add button
        TextView fab = tv("+", 28, th.primaryText);
        fab.setGravity(Gravity.CENTER);
        fab.setTypeface(null,1);
        fab.setBackground(rounded(th.primary, 28));
        fab.setElevation(dp(8));
        fab.setOnClickListener(v -> startActivity(new Intent(this, AddInstallmentActivity.class)));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(58), dp(58));
        fp.gravity = Gravity.BOTTOM | Gravity.END;
        fp.setMargins(0,0,dp(20),dp(20));
        outer.addView(fab, fp);

        setContentView(outer);
        refresh();
    }

    @Override protected void onResume() { super.onResume(); if (list != null) refresh(); }

    String money(long n) { return NumberFormat.getInstance(Locale.US).format(n) + " تومان"; }

    void refresh() {
        list.removeAllViews();
        List<Installment> all = Store.all(this);
        long total = 0; int open=0;
        for (Installment x: all) if (!x.isFinished()) { total += x.amount * (x.totalCount-x.paidCount); open++; }
        summary.setText("📋  " + open + " قسط فعال\n💰  مانده تقریبی: " + money(total));

        List<Installment> visible = new ArrayList<>();
        for (Installment x: all) if (x.isVisibleOnHome()) visible.add(x);

        if (visible.isEmpty()) {
            String msg = all.isEmpty()
                    ? "هنوز قسطی ثبت نکرده‌ای.\nاز دکمه‌ی + شروع کن."
                    : "الان قسطی نزدیک سررسید نیست 👌";
            TextView e = tv(msg, 16, th.muted);
            e.setGravity(Gravity.CENTER);
            list.addView(e, new LinearLayout.LayoutParams(-1,dp(180)));
            return;
        }

        Collections.sort(visible, (a,b)->Long.compare(a.nextDueMillis(), b.nextDueMillis()));
        for (Installment x: visible) addCard(x);
    }

    void addCard(Installment x) {
        int accentColor = x.isFinished() ? th.success : (x.isDueSoonOrOverdue() ? th.danger : th.primary);

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(rounded(th.card, 14));
        row.setClipToOutline(true);

        View accent = new View(this);
        accent.setBackgroundColor(accentColor);
        row.addView(accent, new LinearLayout.LayoutParams(dp(5), -1));

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(12));

        TextView name = tv(x.title, 17, th.text); name.setTypeface(null,1);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-2,-2);
        nameLp.setMargins(0,0,0,dp(3));
        card.addView(name, nameLp);

        TextView sub = tv("قسط " + Math.min(x.paidCount+1,x.totalCount) + " از " + x.totalCount +
                "  •  " + money(x.amount), 14, th.muted);
        card.addView(sub, new LinearLayout.LayoutParams(-2,-2));

        TextView due = tv(x.isFinished() ? "✅ تکمیل شده" : "🗓 سررسید: " + x.dueText(), 14,
                x.isFinished()?th.success:accentColor);
        LinearLayout.LayoutParams dueLp = new LinearLayout.LayoutParams(-2,-2);
        dueLp.setMargins(0,dp(2),0,0);
        card.addView(due, dueLp);

        LinearLayout btnRow = new LinearLayout(this); btnRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(-1,-2);
        btnRowLp.setMargins(0,dp(10),0,0);

        TextView pay = tv(x.isFinished() ? "تکمیل شد" : "✓ ثبت پرداخت", 14, x.isFinished()?th.muted:th.primaryText);
        pay.setGravity(Gravity.CENTER);
        pay.setBackground(rounded(x.isFinished()?th.border:th.primary, 10));
        if (!x.isFinished()) pay.setOnClickListener(v -> {
            x.paidCount++;
            if (x.paidCount >= x.totalCount) x.active = false;
            Store.upsert(this,x);
            AlarmHelper.schedule(this,x);
            refresh();
        });
        LinearLayout.LayoutParams payLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        payLp.setMargins(0,0,dp(8),0);
        btnRow.addView(pay, payLp);

        TextView edit = tv("ویرایش", 14, th.text);
        edit.setGravity(Gravity.CENTER);
        edit.setBackground(roundedStroke(th.card, th.border, 10, 1));
        edit.setOnClickListener(v -> startActivity(new Intent(this,AddInstallmentActivity.class).putExtra("id",x.id)));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(dp(80), dp(40));
        editLp.setMargins(0,0,dp(8),0);
        btnRow.addView(edit, editLp);

        TextView del = tv("🗑", 16, th.danger);
        del.setGravity(Gravity.CENTER);
        del.setBackground(roundedStroke(th.card, th.border, 10, 1));
        del.setOnClickListener(v -> confirmDelete(x));
        btnRow.addView(del, new LinearLayout.LayoutParams(dp(46), dp(40)));

        card.addView(btnRow, btnRowLp);
        row.addView(card, new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0,dp(10),0,0);
        list.addView(row,lp);
    }

    void confirmDelete(Installment x) {
        new AlertDialog.Builder(this)
                .setTitle("حذف قسط")
                .setMessage("«" + x.title + "» حذف بشه؟ این کار قابل بازگشت نیست.")
                .setPositiveButton("حذف", (d,w) -> {
                    AlarmHelper.cancel(this, x.id);
                    Store.delete(this, x.id);
                    refresh();
                })
                .setNegativeButton("انصراف", null)
                .show();
    }
}
