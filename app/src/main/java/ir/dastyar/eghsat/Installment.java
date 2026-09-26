package ir.dastyar.eghsat;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class Installment {
    public long id;
    public String title = "";
    public long amount;
    public int totalCount = 1;
    public int paidCount = 0;
    public int dueDay = 1;
    public long firstDueMillis;
    public boolean active = true;

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("amount", amount);
        o.put("totalCount", totalCount);
        o.put("paidCount", paidCount);
        o.put("dueDay", dueDay);
        o.put("firstDueMillis", firstDueMillis);
        o.put("active", active);
        return o;
    }

    public static Installment fromJson(JSONObject o) {
        Installment x = new Installment();
        x.id = o.optLong("id");
        x.title = o.optString("title");
        x.amount = o.optLong("amount");
        x.totalCount = o.optInt("totalCount", 1);
        x.paidCount = o.optInt("paidCount", 0);
        x.dueDay = o.optInt("dueDay", 1);
        x.firstDueMillis = o.optLong("firstDueMillis");
        x.active = o.optBoolean("active", true);
        return x;
    }

    public String dueText() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nextDueMillis());
        int[] j = PersianDate.toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]);
    }

    public long nextDueMillis() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(firstDueMillis);
        c.add(Calendar.MONTH, paidCount);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(Calendar.DAY_OF_MONTH, Math.min(dueDay, max));
        return c.getTimeInMillis();
    }

    public boolean isFinished() { return paidCount >= totalCount || !active; }

    /** True if the next due date is within 3 days (or already overdue). */
    public boolean isDueSoonOrOverdue() {
        long threeDays = 3L * 24 * 60 * 60 * 1000;
        return nextDueMillis() - System.currentTimeMillis() <= threeDays;
    }

    /**
     * Whether this installment should show on the home screen.
     * A brand-new (never-paid) installment always shows. Once a payment is
     * made, it disappears until 3 days before the next due date. Finished
     * installments stay visible as a completion record.
     */
    public boolean isVisibleOnHome() {
        if (isFinished()) return true;
        if (paidCount == 0) return true;
        return isDueSoonOrOverdue();
    }
}
