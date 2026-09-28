package br.com.itau.geradornotafiscal.config;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.List;

/**
 * Spans guardam só o tipo da exceção (F04-NF-05): a mensagem pode trazer o valor recusado do pedido.
 * Cada exportador de spans é envolvido, então a regra vale para qualquer destino.
 */
@Configuration(proxyBeanMethods = false)
public class ExcecaoSemMensagemNoSpanConfig {

    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");

    @Bean
    static BeanPostProcessor exportadorSemMensagemDeExcecao() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof SpanExporter exportador ? new ExportadorSemMensagem(exportador) : bean;
            }
        };
    }

    private record ExportadorSemMensagem(SpanExporter destino) implements SpanExporter {

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            return destino.export(spans.stream().<SpanData>map(SpanSemMensagem::new).toList());
        }

        @Override
        public CompletableResultCode flush() {
            return destino.flush();
        }

        @Override
        public CompletableResultCode shutdown() {
            return destino.shutdown();
        }
    }

    private static final class SpanSemMensagem extends DelegatingSpanData {

        private SpanSemMensagem(SpanData span) {
            super(span);
        }

        @Override
        public List<EventData> getEvents() {
            return super.getEvents().stream().map(SpanSemMensagem::semMensagem).toList();
        }

        @Override
        public StatusData getStatus() {
            StatusData status = super.getStatus();
            return status.getDescription().isEmpty() ? status : StatusData.create(status.getStatusCode(), "");
        }

        private static EventData semMensagem(EventData evento) {
            if (!"exception".equals(evento.getName())) {
                return evento;
            }
            String tipo = evento.getAttributes().get(EXCEPTION_TYPE);
            Attributes soOTipo = tipo == null ? Attributes.empty() : Attributes.of(EXCEPTION_TYPE, tipo);
            return EventData.create(evento.getEpochNanos(), evento.getName(), soOTipo);
        }
    }
}
