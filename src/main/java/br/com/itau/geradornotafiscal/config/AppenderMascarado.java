package br.com.itau.geradornotafiscal.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
        return MascaraDadosPessoais.precisaMascarar(evento.getFormattedMessage())
                || precisaMascarar(evento.getThrowableProxy(), Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean precisaMascarar(IThrowableProxy erro, Set<IThrowableProxy> vistos) {
        if (erro == null || !vistos.add(erro)) {
            return false;
        }
        if (MascaraDadosPessoais.precisaMascarar(erro.getMessage()) || precisaMascarar(erro.getCause(), vistos)) {
            return true;
        }
        for (IThrowableProxy suprimida : erro.getSuppressed()) {
            if (precisaMascarar(suprimida, vistos)) {
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
     * Cópia da exceção com as mensagens mascaradas, inclusive das causas e das suprimidas. A mensagem começa pelo tipo original,
     * que a cópia não tem, e a stack trace é a mesma.
     */
    static final class ExcecaoMascarada extends RuntimeException {

        private ExcecaoMascarada(String mensagem) {
            super(mensagem);
        }

        static ExcecaoMascarada de(Throwable original) {
            return de(original, new IdentityHashMap<>());
        }

        // As cópias já feitas evitam recursão sem fim quando as causas formam um ciclo.
        private static ExcecaoMascarada de(Throwable original, Map<Throwable, ExcecaoMascarada> copias) {
            ExcecaoMascarada existente = copias.get(original);
            if (existente != null) {
                return existente;
            }
            String mensagem = original.getClass().getName()
                    + (original.getMessage() == null ? "" : ": " + MascaraDadosPessoais.mascarar(original.getMessage()));
            ExcecaoMascarada copia = new ExcecaoMascarada(mensagem);
            copia.setStackTrace(original.getStackTrace());
            copias.put(original, copia);
            if (original.getCause() != null && original.getCause() != original) {
                copia.initCause(de(original.getCause(), copias));
            }
            for (Throwable suprimida : original.getSuppressed()) {
                copia.addSuppressed(de(suprimida, copias));
            }
            return copia;
        }
    }
}
