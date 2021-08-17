package logger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import com.fasterxml.jackson.core.JsonGenerator;
import com.internetitem.logback.elasticsearch.AbstractElasticsearchPublisher;
import com.internetitem.logback.elasticsearch.config.ElasticsearchProperties;
import com.internetitem.logback.elasticsearch.config.HttpRequestHeaders;
import com.internetitem.logback.elasticsearch.config.Property;
import com.internetitem.logback.elasticsearch.config.Settings;
import com.internetitem.logback.elasticsearch.util.AbstractPropertyAndEncoder;
import com.internetitem.logback.elasticsearch.util.ClassicPropertyAndEncoder;
import com.internetitem.logback.elasticsearch.util.ErrorReporter;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.qos.logback.classic.Level.DEBUG;
import static ch.qos.logback.classic.Level.TRACE;

public class GatlingElasticPublisher extends AbstractElasticsearchPublisher<ILoggingEvent> {
    public GatlingElasticPublisher(Context context, ErrorReporter errorReporter, Settings settings, ElasticsearchProperties properties, HttpRequestHeaders headers) throws IOException {
        super(context, errorReporter, settings, properties, headers);
    }

    @Override
    protected AbstractPropertyAndEncoder<ILoggingEvent> buildPropertyAndEncoder(Context context, Property property) {
        return new ClassicPropertyAndEncoder(property, context);
    }

    // TODO Refactoring
    @Override
    protected void serializeCommonFields(JsonGenerator gen, ILoggingEvent event) throws IOException {
        gen.writeObjectField("@timestamp", getTimestamp(event.getTimeStamp()));

        String fullMessage = event.getFormattedMessage();

        boolean wsEvent = event.getLoggerName().contains("io.gatling.http.action.ws");

        if (!wsEvent && (event.getLevel().equals(DEBUG) || event.getLevel().equals(TRACE)) ) {
            gatlingFields(gen, fullMessage);
        } else {
            if (settings.isRawJsonMessage()) {
                gen.writeFieldName("message");
                gen.writeRawValue(fullMessage);
            } else {
                String formattedMessage = fullMessage;
                if (settings.getMaxMessageSize() > 0 && formattedMessage.length() > settings.getMaxMessageSize()) {
                    formattedMessage = formattedMessage.substring(0, settings.getMaxMessageSize()) + "..";
                }
                gen.writeObjectField("message", formattedMessage);

                if (wsEvent) {
                    gen.writeObjectField("protocol", "ws");
                }
            }
        }

        if (settings.isIncludeMdc()) {
            for (Map.Entry<String, String> entry : event.getMDCPropertyMap().entrySet()) {
                gen.writeObjectField(entry.getKey(), entry.getValue());
            }
        }

    }

    private void gatlingFields(JsonGenerator gen, String fullMessage) throws IOException {
        String separator = "=========================";
        if (!fullMessage.contains(separator)) {
            // gatling 3.4.2 write two logs instead one
            gen.writeObjectField("message", fullMessage);
            return;
        }
        String[] partOfMessage = fullMessage.split(separator);
        String infoPart = partOfMessage[0];
        String sessionPart = partOfMessage[1];
        String requestPart = partOfMessage[2];
        String responsePart = partOfMessage[3];

        String statusCodePattern = "status:\\n\\t(\\d{3})";
        String messagePattern = ": (.*)";
        String requestNamePattern = "Request:\\s+?(.*):";
        String sessionPattern = "Session:\\s+?(.*)";
        String methodPattern = "(?<=HTTP request:\\n)(\\w+)\\s(.*)";
        String responseBodyPattern = "body:\\n([\\s\\S]*)\\n";
        String responseHeadersPattern = "headers:\\n\\t([\\s\\S]*)\\n\\nbody";

        String requestHeadersPattern = "headers:\\n\\t([\\s\\S]*)\\nbody";
        String requestBodyPattern = "body:([\\s\\S]*)\\n";

        if (requestPart.contains("byteArraysBody")) {
            requestHeadersPattern = "headers=\\n([\\s\\S]*)byteArraysBody";
        }

        String statusCode = "";
        String message = "";
        String session = "";
        String method = "";
        String url = "";
        String responseBody = "";
        String responseHeaders = "";
        String requestHeaders = "";
        String requestBody = "";
        String requestName = "";

        Matcher mStatusCode = Pattern.compile(statusCodePattern).matcher(responsePart);
        Matcher mMessage = Pattern.compile(messagePattern).matcher(infoPart);
        Matcher mSession = Pattern.compile(sessionPattern).matcher(sessionPart);
        Matcher mMethod = Pattern.compile(methodPattern).matcher(requestPart);
        Matcher mResponseBody = Pattern.compile(responseBodyPattern).matcher(responsePart);
        Matcher mResponseHeaders = Pattern.compile(responseHeadersPattern).matcher(responsePart);
        Matcher mRequestHeaders = Pattern.compile(requestHeadersPattern).matcher(requestPart);
        Matcher mRequestBody = Pattern.compile(requestBodyPattern).matcher(requestPart);
        Matcher mRequestName = Pattern.compile(requestNamePattern).matcher(infoPart);

        while (mStatusCode.find()) {
            statusCode = mStatusCode.group(1);
        }
        while (mMessage.find()) {
            message = mMessage.group(1);
        }
        while (mSession.find()) {
            session = mSession.group(1);
        }
        while (mMethod.find()) {
            method = mMethod.group(1);
            url = mMethod.group(2);
        }
        while (mResponseBody.find()) {
            responseBody = mResponseBody.group(1);
        }
        while (mResponseHeaders.find()) {
            responseHeaders = mResponseHeaders.group(1).replaceAll("\t", "");
        }
        while (mRequestHeaders.find()) {
            requestHeaders = mRequestHeaders.group(1).replaceAll("\t", "");
        }
        while (mRequestBody.find()) {
            requestBody = mRequestBody.group(1);
        }
        while (mRequestName.find()) {
            requestName = mRequestName.group(1);
        }

        if (responseBody.isEmpty()) {
            responseBody = "%empty%";
        }

        if (requestBody.isEmpty()) {
            requestBody = "%empty%";
        }

        String scenario;
        int usedId;

        String[] sessionList = session.replace("Session(", "").split(",");
        scenario = sessionList[0];
        usedId = Integer.parseInt(sessionList[1]);


        gen.writeObjectField("message", message);
        gen.writeObjectField("method", method);
        gen.writeObjectField("url", url);
        gen.writeObjectField("request_body", requestBody);
        gen.writeObjectField("request_headers", requestHeaders);
        gen.writeObjectField("status_code", statusCode);
        gen.writeObjectField("response_body", responseBody);
        gen.writeObjectField("response_headers", responseHeaders);
        gen.writeObjectField("session", session);
        gen.writeObjectField("scenario", scenario);
        gen.writeObjectField("usedId", usedId);
        gen.writeObjectField("request_name", requestName);
        gen.writeObjectField("protocol", "http");
    }

}
