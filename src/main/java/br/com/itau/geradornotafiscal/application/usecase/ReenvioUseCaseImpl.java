package br.com.itau.geradornotafiscal.application.usecase;

import br.com.itau.geradornotafiscal.application.exception.PedidoDivergenteException;
import br.com.itau.geradornotafiscal.application.port.in.ReenvioUseCase;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort;
import br.com.itau.geradornotafiscal.application.port.out.NotaFiscalPersistenciaPort.NotaGuardada;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReenvioUseCaseImpl implements ReenvioUseCase {

    private final NotaFiscalPersistenciaPort notaFiscalPersistenciaPort;

    @Override
    public Optional<NotaFiscal> notaDoReenvio(Long idPedido, String hashPedido) {
        Optional<NotaGuardada> guardada = notaFiscalPersistenciaPort.buscar(idPedido);
        if (guardada.isPresent() && !guardada.get().hashPedido().equals(hashPedido)) {
            throw new PedidoDivergenteException(idPedido);
        }
        return guardada.map(NotaGuardada::nota);
    }
}
