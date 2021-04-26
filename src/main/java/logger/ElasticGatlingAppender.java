package logger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.internetitem.logback.elasticsearch.AbstractElasticsearchAppender;
import com.internetitem.logback.elasticsearch.config.Settings;

import java.io.IOException;

public class ElasticGatlingAppender extends AbstractElasticsearchAppender<ILoggingEvent> {
    public ElasticGatlingAppender() {
    }

    public ElasticGatlingAppender(Settings settings) {
        super(settings);
    }

    @Override
    protected void appendInternal(ILoggingEvent eventObject) {

        String targetLogger = eventObject.getLoggerName();

        String loggerName = settings.getLoggerName();
        if (loggerName != null && loggerName.equals(targetLogger)) {
            return;
        }

        String errorLoggerName = settings.getErrorLoggerName();
        if (errorLoggerName != null && errorLoggerName.equals(targetLogger)) {
            return;
        }

        eventObject.prepareForDeferredProcessing();
        if (settings.isIncludeCallerData()) {
            eventObject.getCallerData();
        }

        publishEvent(eventObject);
    }

    protected GatlingElasticPublisher buildElasticsearchPublisher() throws IOException {
        return new GatlingElasticPublisher(getContext(), errorReporter, settings, elasticsearchProperties, headers);
    }
}
