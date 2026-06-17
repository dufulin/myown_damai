package com.myown.damai.pay.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.pay.entity.PayBill;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Generates Alipay page-pay forms and verifies Alipay notify signatures.
 */
@Component
public class AlipayPagePayClient {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String gatewayUrl;
    private final String appId;
    private final String merchantPrivateKey;
    private final String alipayPublicKey;
    private final String notifyUrl;
    private final String returnUrl;
    private final boolean verifySignature;

    /**
     * Creates the Alipay client with configurable merchant credentials.
     */
    public AlipayPagePayClient(
            ObjectMapper objectMapper,
            @Value("${damai.pay.alipay.enabled:false}") boolean enabled,
            @Value("${damai.pay.alipay.gateway-url:https://openapi-sandbox.dl.alipaydev.com/gateway.do}") String gatewayUrl,
            @Value("${damai.pay.alipay.app-id:}") String appId,
            @Value("${damai.pay.alipay.merchant-private-key:}") String merchantPrivateKey,
            @Value("${damai.pay.alipay.alipay-public-key:}") String alipayPublicKey,
            @Value("${damai.pay.alipay.notify-url:http://localhost:8080/api/pay/alipay/notify}") String notifyUrl,
            @Value("${damai.pay.alipay.return-url:http://localhost:5173}") String returnUrl,
            @Value("${damai.pay.alipay.verify-signature:false}") boolean verifySignature
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.gatewayUrl = gatewayUrl;
        this.appId = appId;
        this.merchantPrivateKey = merchantPrivateKey;
        this.alipayPublicKey = alipayPublicKey;
        this.notifyUrl = notifyUrl;
        this.returnUrl = returnUrl;
        this.verifySignature = verifySignature;
    }

    /**
     * Builds an HTML form that submits the payment request to Alipay.
     */
    public String buildPagePayForm(PayBill bill) {
        if (!enabled) {
            return buildDisabledModeForm(bill);
        }
        ensureAlipayConfigured();
        TreeMap<String, String> params = buildCommonParams(bill);
        params.put("sign", sign(params));
        StringBuilder form = new StringBuilder();
        form.append("<form id=\"alipay_submit\" name=\"alipay_submit\" action=\"")
                .append(escapeHtml(gatewayUrl))
                .append("\" method=\"post\">");
        params.forEach((key, value) -> form.append("<input type=\"hidden\" name=\"")
                .append(escapeHtml(key))
                .append("\" value=\"")
                .append(escapeHtml(value))
                .append("\"/>"));
        form.append("<input type=\"submit\" value=\"Pay\" style=\"display:none;\"/>")
                .append("</form><script>document.forms['alipay_submit'].submit();</script>");
        return form.toString();
    }

    /**
     * Verifies an Alipay asynchronous notify signature when verification is enabled.
     */
    public boolean verifyNotify(Map<String, String> notifyParams) {
        if (!verifySignature) {
            return true;
        }
        if (!StringUtils.hasText(alipayPublicKey) || !StringUtils.hasText(notifyParams.get("sign"))) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(parsePublicKey(alipayPublicKey));
            signature.update(buildSignContent(notifyParams).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(notifyParams.get("sign")));
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Builds the required common Alipay request parameters.
     */
    private TreeMap<String, String> buildCommonParams(PayBill bill) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("app_id", appId);
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "UTF-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", TIMESTAMP_FORMATTER.format(LocalDateTime.now(CHINA_ZONE)));
        params.put("version", "1.0");
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("biz_content", buildBizContent(bill));
        return params;
    }

    /**
     * Builds the business payload required by alipay.trade.page.pay.
     */
    private String buildBizContent(PayBill bill) {
        try {
            Map<String, String> bizContent = new TreeMap<>();
            bizContent.put("out_trade_no", bill.outOrderNo);
            bizContent.put("total_amount", bill.payAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            bizContent.put("subject", bill.subject);
            bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
            return objectMapper.writeValueAsString(bizContent);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("ALIPAY_REQUEST_BUILD_FAILED", "failed to build alipay request", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Signs sorted Alipay parameters with RSA2.
     */
    private String sign(TreeMap<String, String> params) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(merchantPrivateKey));
            signature.update(buildSignContent(params).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new BusinessException("ALIPAY_SIGN_FAILED", "failed to sign alipay request", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Builds canonical key-value content for signing or verification.
     */
    private String buildSignContent(Map<String, String> params) {
        TreeMap<String, String> sortedParams = new TreeMap<>(params);
        StringBuilder content = new StringBuilder();
        sortedParams.forEach((key, value) -> {
            if ("sign".equals(key) || !StringUtils.hasText(value)) {
                return;
            }
            if (content.length() > 0) {
                content.append("&");
            }
            content.append(key).append("=").append(value);
        });
        return content.toString();
    }

    /**
     * Builds a local placeholder form when real Alipay integration is disabled.
     */
    private String buildDisabledModeForm(PayBill bill) {
        String message = "Alipay is disabled. Pay bill " + bill.payNumber + " for order " + bill.outOrderNo + " has been created.";
        return "<div>" + escapeHtml(message) + "</div>";
    }

    /**
     * Ensures required Alipay credentials are present before creating a real request.
     */
    private void ensureAlipayConfigured() {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(merchantPrivateKey)) {
            throw new BusinessException("ALIPAY_NOT_CONFIGURED", "alipay app id or private key is missing", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Parses a PEM or plain Base64 merchant private key.
     */
    private PrivateKey parsePrivateKey(String keyText) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(cleanPem(keyText));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    /**
     * Parses a PEM or plain Base64 Alipay public key.
     */
    private PublicKey parsePublicKey(String keyText) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(cleanPem(keyText));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    /**
     * Removes PEM decorations and whitespace.
     */
    private String cleanPem(String keyText) {
        return keyText
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    /**
     * Escapes HTML attribute values used in the generated payment form.
     */
    private String escapeHtml(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
