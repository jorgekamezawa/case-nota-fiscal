package br.com.itau.geradornotafiscal.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppenderMascaradoTest {

    private static final String CPF = "887.403.470-95";

    private final LoggerContext contexto = new LoggerContext();
    private final ListAppender<ILoggingEvent> destino = new ListAppender<>();
    private final AppenderMascarado appender = new AppenderMascarado();

    @BeforeEach
    void preparar() {
        contexto.setMDCAdapter(new LogbackMDCAdapter());
        destino.setContext(contexto);
        destino.start();
        appender.setContext(contexto);
        appender.addAppender(destino);
        appender.start();
    }

    @Test
    void f04Nf05_mascaraExcecaoSuprimida() {
        RuntimeException erro = new RuntimeException("falha");
        erro.addSuppressed(new IllegalStateException("suprimida " + CPF));

        appender.doAppend(evento(erro));

        Throwable enviado = ((ThrowableProxy) destino.list.getFirst().getThrowableProxy()).getThrowable();
        assertThat(enviado.getSuppressed()).singleElement()
                .satisfies(s -> assertThat(s.getMessage()).isEqualTo("java.lang.IllegalStateException: suprimida ***"));
    }

    @Test
    void f04Nf05_causasEmCicloNaoTravamOAppender() {
        RuntimeException a = new RuntimeException("a " + CPF);
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        appender.doAppend(evento(a));

        Throwable enviado = ((ThrowableProxy) destino.list.getFirst().getThrowableProxy()).getThrowable();
        assertThat(enviado.getMessage()).isEqualTo("java.lang.RuntimeException: a ***");
        assertThat(enviado.getCause().getCause()).isSameAs(enviado);
    }

    private LoggingEvent evento(Throwable erro) {
        return new LoggingEvent(AppenderMascaradoTest.class.getName(), contexto.getLogger("teste"), Level.ERROR,
                "erro", erro, null);
    }
}
