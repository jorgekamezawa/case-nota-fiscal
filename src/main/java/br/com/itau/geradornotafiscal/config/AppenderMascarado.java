package br.com.itau.geradornotafiscal.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;

import java.util.Iterator;

/**
 * Ponto central do mascaramento (F04-NF-05): todo log passa por aqui antes do console e do OTLP.
 * Por implementar {@link AppenderAttachable}, a instalação do appender do OpenTelemetry o encontra aqui dentro.
 */
public class AppenderMascarado extends AppenderBase<ILoggingEvent> implements AppenderAttachable<ILoggingEvent> {

    private final AppenderAttachableImpl<ILoggingEvent> destinos = new AppenderAttachableImpl<>();

    @Override
    protected void append(ILoggingEvent evento) {
        destinos.appendLoopOnAppenders(precisaMascarar(evento) ? mascarado(evento) : evento);
    }

    private static boolean precisaMascarar(ILoggingEvent evento) {
        if (MascaraDadosPessoais.precisaMascarar(evento.getFormattedMessage())) {
            return true;
        }
        for (IThrowableProxy erro = evento.getThrowableProxy(); erro != null; erro = erro.getCause()) {
            if (MascaraDadosPessoais.precisaMascarar(erro.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static ILoggingEvent mascarado(ILoggingEvent original) {
        LoggingEvent copia = new LoggingEvent();
        copia.setLoggerContextRemoteView(original.getLoggerContextVO());
        copia.setSequenceNumber(original.getSequenceNumber());
        copia.setLoggerName(original.getLoggerName());
        copia.setLevel(original.getLevel());
        copia.setMessage(MascaraDadosPessoais.mascarar(original.getFormattedMessage()));
        copia.setInstant(original.getInstant());
        copia.setThreadName(original.getThreadName());
        copia.setMDCPropertyMap(original.getMDCPropertyMap());
        copia.setKeyValuePairs(original.getKeyValuePairs());
        if (original.getMarkerList() != null) {
            original.getMarkerList().forEach(copia::addMarker);
        }
        if (original.getThrowableProxy() instanceof ThrowableProxy proxy) {
            copia.setThrowableProxy(new ThrowableProxy(ExcecaoMascarada.de(proxy.getThrowable())));
        }
        return copia;
    }

    @Override
    public void addAppender(Appender<ILoggingEvent> appender) {
        destinos.addAppender(appender);
    }

    @Override
    public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
        return destinos.iteratorForAppenders();
    }

    @Override
    public Appender<ILoggingEvent> getAppender(String nome) {
        return destinos.getAppender(nome);
    }

    @Override
    public boolean isAttached(Appender<ILoggingEvent> appender) {
        return destinos.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
        destinos.detachAndStopAllAppenders();
    }

    @Override
    public boolean detachAppender(Appender<ILoggingEvent> appender) {
        return destinos.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String nome) {
        return destinos.detachAppender(nome);
    }

    /**
     * Cópia da exceção com as mensagens mascaradas, inclusive das causas. A mensagem começa pelo tipo original,
     * que a cópia não tem, e a stack trace é a mesma.
     */
    static final class ExcecaoMascarada extends RuntimeException {

        private ExcecaoMascarada(String mensagem, Throwable causa) {
            super(mensagem, causa, false, true);
        }

        static ExcecaoMascarada de(Throwable original) {
            Throwable causa = original.getCause() == null || original.getCause() == original
                    ? null : de(original.getCause());
            String mensagem = original.getClass().getName()
                    + (original.getMessage() == null ? "" : ": " + MascaraDadosPessoais.mascarar(original.getMessage()));
            ExcecaoMascarada copia = new ExcecaoMascarada(mensagem, causa);
            copia.setStackTrace(original.getStackTrace());
            return copia;
        }
    }
}
