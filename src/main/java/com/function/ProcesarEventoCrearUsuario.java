package com.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.DTO.UsuarioDTO;
import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.*;

public class ProcesarEventoCrearUsuario {

    @FunctionName("ProcesarEventoCrearUsuario")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "event/usuario"
        ) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Evento recibido por Webhook");

        try {
            ObjectMapper mapper = new ObjectMapper();
            String body = request.getBody().orElse("[]");

            JsonNode[] events = mapper.readValue(body, JsonNode[].class);

            for (JsonNode event : events) {
                String eventType = event.get("eventType").asText();

                if ("Microsoft.EventGrid.SubscriptionValidationEvent".equals(eventType)) {
                    String validationCode = event.get("data").get("validationCode").asText();

                    Map<String, String> response = new HashMap<>();
                    response.put("validationResponse", validationCode);

                    return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(response)
                        .build();
                }

                if ("UsuarioCreado".equals(eventType)) {
                    UsuarioDTO usuario = mapper.treeToValue(event.get("data"), UsuarioDTO.class);

                    // 🔧 Aquí sigue tu lógica de base de datos (igual que antes)
                    WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
                    String tmpDir = System.getProperty("java.io.tmpdir");
                    String walletPath = tmpDir.contains("\\") ? tmpDir.replace("\\", "/") : tmpDir;
                    String oracleUrl = "jdbc:oracle:thin:@et2xa97ns8rti1vt_tp?TNS_ADMIN=" + walletPath;
                    String oracleUser = "duoc_fullstack";
                    String oraclePass = "Eduardocr#2610";

                    try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
                        conn.setAutoCommit(false);

                        String sqlUsuario = "INSERT INTO USUARIO (NOMBRE, RUT, DIRECCION, COMUNA, ROL_ID) VALUES (?, ?, ?, ?, ?)";
                        PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario, new String[]{"ID"});
                        stmtUsuario.setString(1, usuario.getNombre());
                        stmtUsuario.setString(2, usuario.getRut());
                        stmtUsuario.setString(3, usuario.getDireccion());
                        stmtUsuario.setString(4, usuario.getComuna());
                        stmtUsuario.setLong(5, usuario.getRolId());
                        stmtUsuario.executeUpdate();

                        ResultSet rs = stmtUsuario.getGeneratedKeys();
                        Long nuevoUsuarioId = rs.next() ? rs.getLong(1) : null;
                        if (nuevoUsuarioId == null) {
                            conn.rollback();
                            context.getLogger().severe("No se pudo obtener ID del usuario insertado.");
                            continue;
                        }

                        String sqlAuth = "INSERT INTO AUTH (USERNAME, PASSWORD, USUARIO_ID) VALUES (?, ?, ?)";
                        PreparedStatement stmtAuth = conn.prepareStatement(sqlAuth);
                        stmtAuth.setString(1, usuario.getUsername());
                        stmtAuth.setString(2, usuario.getPassword());
                        stmtAuth.setLong(3, nuevoUsuarioId);
                        stmtAuth.executeUpdate();

                        conn.commit();
                        context.getLogger().info("Usuario creado con ID: " + nuevoUsuarioId);
                    }
                }
            }

            return request.createResponseBuilder(HttpStatus.OK)
                .body("Evento procesado correctamente.")
                .build();

        } catch (Exception e) {
            context.getLogger().severe("Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error procesando el evento: " + e.getMessage())
                .build();
        }
    }
}
