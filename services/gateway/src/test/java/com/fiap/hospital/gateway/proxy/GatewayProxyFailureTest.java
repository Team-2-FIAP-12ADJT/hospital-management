package com.fiap.hospital.gateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;

import org.junit.jupiter.api.Test;

class GatewayProxyFailureTest {

    @Test
    void timeoutDeConexaoVira503MesmoSendoTimeout() {
        assertThat(GatewayProxyFilter.transportFailureStatus(
            new HttpConnectTimeoutException("connect timeout")
        )).isEqualTo(503);
    }

    @Test
    void conexaoInterrompidaVira503() {
        assertThat(GatewayProxyFilter.transportFailureStatus(
            new ConnectException("connection reset")
        )).isEqualTo(503);
    }

    @Test
    void timeoutDuranteLeituraVira504() {
        assertThat(GatewayProxyFilter.transportFailureStatus(
            new SocketTimeoutException("read timeout")
        )).isEqualTo(504);
    }
}
