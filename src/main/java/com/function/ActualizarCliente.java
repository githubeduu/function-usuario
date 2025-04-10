package com.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.Optional;

public class ActualizarCliente {

    @FunctionName("ActualizarCliente")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.PUT},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "usuario/{id}"
        )
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id,
        final ExecutionContext context
    ) {
        context.getLogger().info("Procesando actualización del cliente con ID: " + id);

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

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode body = mapper.readTree(request.getBody().orElse("{}"));

            String contrasena = body.get("contrasena").asText();
            String direccion = body.get("direccion").asText();
            String comuna = body.get("comuna").asText();
            Long rolId = body.get("rolId").asLong();

            try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
                conn.setAutoCommit(false); // comenzar transacción

                // 1. Actualizar en la tabla USUARIO
                String sqlUsuario = "UPDATE USUARIO SET DIRECCION = ?, COMUNA = ?, ROL_ID = ? WHERE ID = ?";
                PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario);
                stmtUsuario.setString(1, direccion);
                stmtUsuario.setString(2, comuna);
                stmtUsuario.setLong(3, rolId);
                stmtUsuario.setString(4, id);
                int filasUsuario = stmtUsuario.executeUpdate();

                // 2. Actualizar en la tabla AUTH
                String sqlAuth = "UPDATE AUTH SET PASSWORD = ? WHERE USUARIO_ID = ?";
                PreparedStatement stmtAuth = conn.prepareStatement(sqlAuth);
                stmtAuth.setString(1, contrasena);
                stmtAuth.setString(2, id);
                int filasAuth = stmtAuth.executeUpdate();

                if (filasUsuario > 0 || filasAuth > 0) {
                    conn.commit();
                    return request.createResponseBuilder(HttpStatus.OK)
                        .body("Cliente actualizado correctamente.")
                        .build();
                } else {
                    conn.rollback();
                    return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .body("No se encontró cliente con ID: " + id)
                        .build();
                }

            } catch (SQLException e) {
                context.getLogger().severe("Error SQL: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en base de datos: " + e.getMessage())
                    .build();
            }

        } catch (Exception e) {
            context.getLogger().severe("Error JSON: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Error al procesar JSON: " + e.getMessage())
                .build();
        }
    }
}
