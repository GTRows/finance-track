package com.fintrack.notification;

public final class MailTemplate {

    private MailTemplate() {}

    private static final String SHELL = """
            <!doctype html>
            <html>
              <body style="margin:0;padding:0;background:#0f172a;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;color:#e2e8f0">
                <table width="100%%" cellpadding="0" cellspacing="0" role="presentation">
                  <tr>
                    <td align="center" style="padding:32px 16px">
                      <table width="560" cellpadding="0" cellspacing="0" role="presentation" style="background:#1e293b;border-radius:16px;overflow:hidden;border:1px solid #334155">
                        <tr>
                          <td style="padding:28px 32px;border-bottom:1px solid #334155">
                            <span style="font-size:18px;font-weight:600;color:#f8fafc">FinTrack Pro</span>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 32px;font-size:14px;line-height:1.6;color:#cbd5e1">
                            %s
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:20px 32px;background:#0f172a;font-size:12px;color:#64748b;border-top:1px solid #334155">
                            Sent by FinTrack Pro, your personal finance system.
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """;

    public static String wrap(String innerHtml) {
        return String.format(SHELL, innerHtml);
    }

    public static String testMessage(String recipient) {
        return wrap("""
                <h2 style="margin:0 0 12px;color:#f8fafc;font-size:20px">SMTP test</h2>
                <p>If you are reading this at <strong>%s</strong>, mail delivery is wired up correctly.</p>
                """.formatted(escape(recipient)));
    }

    public static String actionButton(String url, String label) {
        return """
                <p style="margin:24px 0">
                  <a href="%s" style="display:inline-block;padding:12px 20px;background:#3b82f6;color:#f8fafc;text-decoration:none;border-radius:10px;font-weight:600;font-size:14px">%s</a>
                </p>
                <p style="font-size:12px;color:#64748b;word-break:break-all">Or open this link: %s</p>
                """.formatted(escape(url), escape(label), escape(url));
    }

    public static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
