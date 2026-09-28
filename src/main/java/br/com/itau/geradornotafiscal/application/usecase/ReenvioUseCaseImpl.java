package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.port.in.ReenvioUseCase;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.service.reenvio.RegraDoReenvio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReenvioUseCaseImpl implements ReenvioUseCase {

    private final NotaFiscalPersistenciaPort notaFiscalPersistenciaPort;
    private final RegraDoReenvio regraDoReenvio;

    @Override
    public Optional<NotaFiscal> executar(Long idPedido, String hashPedido) {
        return notaFiscalPersistenciaPort.buscar(idPedido).map(guardada -> {
            regraDoReenvio.conferir(idPedido, guardada.hashPedido(), hashPedido);
            return guardada.nota();
        });
    }
}
