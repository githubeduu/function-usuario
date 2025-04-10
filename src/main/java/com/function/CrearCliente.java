package com.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.DTO.UsuarioDTO;
import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.Optional;

public class CrearCliente {

    @FunctionName("CrearCliente")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "usuario"
        )
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Procesando creación de cliente");

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
            UsuarioDTO usuario = mapper.readValue(request.getBody().orElse(""), UsuarioDTO.class);

            try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
                conn.setAutoCommit(false); // iniciar transacción

                // 1. Insertar en usuario
                String sqlUsuario = "INSERT INTO USUARIO (NOMBRE, RUT, DIRECCION, COMUNA, ROL_ID) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario, new String[]{"ID"});
                stmtUsuario.setString(1, usuario.getNombre());
                stmtUsuario.setString(2, usuario.getRut());
                stmtUsuario.setString(3, usuario.getDireccion());
                stmtUsuario.setString(4, usuario.getComuna());
                stmtUsuario.setLong(5, usuario.getRolId());
                stmtUsuario.executeUpdate();

                ResultSet rs = stmtUsuario.getGeneratedKeys();
                Long nuevoUsuarioId = null;
                if (rs.next()) {
                    nuevoUsuarioId = rs.getLong(1);
                } else {
                    conn.rollback();
                    return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("No se pudo obtener ID del usuario insertado.")
                        .build();
                }

                // 2. Insertar en auth
                String sqlAuth = "INSERT INTO AUTH (USERNAME, PASSWORD, USUARIO_ID) VALUES (?, ?, ?)";
                PreparedStatement stmtAuth = conn.prepareStatement(sqlAuth);
                stmtAuth.setString(1, usuario.getUsername());
                stmtAuth.setString(2, usuario.getPassword());
                stmtAuth.setLong(3, nuevoUsuarioId);
                stmtAuth.executeUpdate();

                conn.commit();
                return request.createResponseBuilder(HttpStatus.CREATED)
                    .body("Usuario creado con ID " + nuevoUsuarioId)
                    .build();

            } catch (SQLException ex) {
                context.getLogger().severe("Error durante inserción: " + ex.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error en base de datos: " + ex.getMessage())
                    .build();
            }

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Error al procesar JSON: " + e.getMessage())
                .build();
        }
    }
}
