package com.function;

import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.Optional;

public class EliminarCliente {

    @FunctionName("EliminarCliente")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.DELETE},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "usuario/{id}"
        )
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id,
        final ExecutionContext context
    ) {
        context.getLogger().info("Procesando eliminación de cliente con ID: " + id);

        try {
            WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al preparar el wallet: " + e.getMessage())
                .build();
        }

        String tmpDir = System.getProperty("java.io.tmpdir");
        String walletPath = tmpDir.contains("\\") ? tmpDir.replace("\\", "/") : tmpDir;
        String oracleUrl = "jdbc:oracle:thin:@et2xa97ns8rti1vt_tp?TNS_ADMIN=" + walletPath;
        String oracleUser = "duoc_fullstack";
        String oraclePass = "Eduardocr#2610";

        try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
            conn.setAutoCommit(false); // iniciar transacción

            // 1. Eliminar en tabla AUTH (hijo)
            String sqlAuth = "DELETE FROM AUTH WHERE USUARIO_ID = ?";
            PreparedStatement stmtAuth = conn.prepareStatement(sqlAuth);
            stmtAuth.setString(1, id);
            stmtAuth.executeUpdate();

            // 2. Eliminar en tabla USUARIO (padre)
            String sqlUsuario = "DELETE FROM USUARIO WHERE ID = ?";
            PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario);
            stmtUsuario.setString(1, id);
            int filas = stmtUsuario.executeUpdate();

            if (filas > 0) {
                conn.commit();
                return request.createResponseBuilder(HttpStatus.OK)
                    .body("Cliente eliminado correctamente.")
                    .build();
            } else {
                conn.rollback();
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body("Cliente no encontrado.")
                    .build();
            }

        } catch (SQLException e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al eliminar cliente: " + e.getMessage())
                .build();
        }
    }
}
