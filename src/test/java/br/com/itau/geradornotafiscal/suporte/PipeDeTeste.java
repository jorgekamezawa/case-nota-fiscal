package br.com.itau.geradornotafiscal.suporte;

import br.com.itau.geradornotafiscal.config.FilasDeTarefas;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetRecordsResponse;
import software.amazon.awssdk.services.dynamodb.model.Record;
import software.amazon.awssdk.services.dynamodb.model.Shard;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.event.ruler.Machine;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Faz localmente o papel do Pipe, que não tem emulador (RFC-0001, risco 9): lê o Streams da tabela de tarefas no
 * DynamoDB Local, aplica o filtro versionado em {@code infra/pipes/filtro-tarefas.json} com o Event Ruler (a
 * biblioteca da AWS que aplica os padrões do EventBridge) e publica a mensagem de {@code infra/pipes/mensagem-tarefa.json}
 * na fila do sistema. É uma aproximação: a AWS não declara que os Pipes usam essa biblioteca (E02-NF-03).
 */
public class PipeDeTeste {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path FILTRO = Path.of("infra/pipes/filtro-tarefas.json");
    private static final Path MENSAGEM = Path.of("infra/pipes/mensagem-tarefa.json");

    private final DynamoDbClient dynamoDb;
    private final DynamoDbStreamsClient streams;
    private final SqsClient sqs;
    private final FilasDeTarefas filas;
    private final String tabela;
    private final Machine filtros = new Machine();
    private final Set<String> lidos = new HashSet<>();

    public PipeDeTeste(DynamoDbClient dynamoDb, SqsClient sqs, FilasDeTarefas filas, String tabela) throws Exception {
        this.dynamoDb = dynamoDb;
        this.sqs = sqs;
        this.filas = filas;
        this.tabela = tabela;
        this.streams = DynamoDbStreamsClient.builder()
                .endpointOverride(URI.create(EmuladoresAws.endpointDynamoDb()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
        String filtro = Files.readString(FILTRO, StandardCharsets.UTF_8);
        for (Sistema sistema : Sistema.values()) {
            filtros.addRule(sistema.name(), filtro.replace("${sistema}", sistema.name()));
        }
    }

    /** Eventos do Streams ainda não lidos, no formato que o Pipe recebe. */
    public List<String> eventosNovos() {
        String stream = dynamoDb.describeTable(t -> t.tableName(tabela)).table().latestStreamArn();
        List<String> eventos = new ArrayList<>();
        for (Shard shard : streams.describeStream(d -> d.streamArn(stream)).streamDescription().shards()) {
            String iterador = streams.getShardIterator(i -> i.streamArn(stream).shardId(shard.shardId())
                    .shardIteratorType(ShardIteratorType.TRIM_HORIZON)).shardIterator();
            while (iterador != null) {
                String atual = iterador;
                GetRecordsResponse resposta = streams.getRecords(r -> r.shardIterator(atual));
                for (Record registro : resposta.records()) {
                    if (lidos.add(registro.dynamodb().sequenceNumber())) {
                        eventos.add(evento(registro));
                    }
                }
                iterador = resposta.records().isEmpty() ? null : resposta.nextShardIterator();
            }
        }
        return eventos;
    }

    /** Sistemas cujo filtro aceita o evento. */
    public List<String> sistemasQueAceitam(String evento) throws Exception {
        return filtros.rulesForJSONEvent(evento);
    }

    /** Lê os eventos novos e publica na fila do sistema os que o filtro aceita; devolve quantos publicou. */
    public int transportar() throws Exception {
        int publicadas = 0;
        String modelo = Files.readString(MENSAGEM, StandardCharsets.UTF_8);
        for (String evento : eventosNovos()) {
            for (String sistema : sistemasQueAceitam(evento)) {
                var imagem = JSON.readTree(evento).get("dynamodb").get("NewImage");
                String mensagem = modelo
                        .replace("<$.dynamodb.NewImage.id_pedido.N>", imagem.get("id_pedido").get("N").asString())
                        .replace("<$.dynamodb.NewImage.sistema.S>", imagem.get("sistema").get("S").asString());
                sqs.sendMessage(envio -> envio.queueUrl(filas.fila(Sistema.valueOf(sistema))).messageBody(mensagem));
                publicadas++;
            }
        }
        return publicadas;
    }

    // Registro do Streams no formato JSON do evento que o Pipe filtra (eventName e dynamodb com as imagens).
    private static String evento(Record registro) {
        ObjectNode evento = JSON.createObjectNode().put("eventName", registro.eventNameAsString());
        ObjectNode dynamodb = evento.putObject("dynamodb");
        imagem(dynamodb, "Keys", registro.dynamodb().keys());
        imagem(dynamodb, "NewImage", registro.dynamodb().newImage());
        imagem(dynamodb, "OldImage", registro.dynamodb().oldImage());
        return evento.toString();
    }

    private static void imagem(ObjectNode dynamodb, String nome, Map<String, AttributeValue> atributos) {
        if (atributos == null || atributos.isEmpty()) {
            return;
        }
        ObjectNode imagem = dynamodb.putObject(nome);
        atributos.forEach((atributo, valor) -> {
            if (valor.s() != null) {
                imagem.putObject(atributo).put("S", valor.s());
            } else if (valor.n() != null) {
                imagem.putObject(atributo).put("N", valor.n());
            }
        });
    }
}
