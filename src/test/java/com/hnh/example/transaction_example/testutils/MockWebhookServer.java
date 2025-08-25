package com.hnh.example.transaction_example.testutils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Mock webhook server for testing webhook functionality
 */
public class MockWebhookServer {

    private final WireMockServer wireMockServer;

    public MockWebhookServer() {
        this.wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    }

    public MockWebhookServer(int port) {
        this.wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port));
    }

    public void start() {
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    public void stop() {
        wireMockServer.stop();
    }

    public void reset() {
        wireMockServer.resetAll();
    }

    public int getPort() {
        return wireMockServer.port();
    }

    public String getBaseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }

    // Helper methods for common webhook stubs

    public void stubSuccessfulWebhook() {
        stubFor(post(urlPathMatching("/webhooks/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"received\": true}")));
    }

    public void stubSuccessfulWebhook(String path) {
        stubFor(post(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"received\": true}")));
    }

    public void stubFailedWebhook() {
        stubFor(post(urlPathMatching("/webhooks/.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Internal server error\"}")));
    }

    public void stubFailedWebhook(String path) {
        stubFor(post(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Internal server error\"}")));
    }

    public void stubWebhookWithDelay(int delayMs) {
        stubFor(post(urlPathMatching("/webhooks/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"received\": true}")
                        .withFixedDelay(delayMs)));
    }

    public void stubWebhookWithSequentialResponses(int... statusCodes) {
        var scenario = "webhook-retry";

        for (int i = 0; i < statusCodes.length; i++) {
            var stubMapping = post(urlPathMatching("/webhooks/.*"))
                    .inScenario(scenario);

            if (i == 0) {
                stubMapping = stubMapping.whenScenarioStateIs("Started");
            } else {
                stubMapping = stubMapping.whenScenarioStateIs("Attempt" + i);
            }

            if (i < statusCodes.length - 1) {
                stubMapping = stubMapping.willSetStateTo("Attempt" + (i + 1));
            }

            stubFor(stubMapping.willReturn(aResponse().withStatus(statusCodes[i])));
        }
    }

    // Verification helpers

    public void verifyWebhookCalled() {
        verify(exactly(1), postRequestedFor(urlPathMatching("/webhooks/.*")));
    }

    public void verifyWebhookCalled(int count) {
        verify(exactly(count), postRequestedFor(urlPathMatching("/webhooks/.*")));
    }

    public void verifyWebhookCalledWithPath(String path) {
        verify(exactly(1), postRequestedFor(urlEqualTo(path)));
    }

    public void verifyWebhookCalledWithHeader(String header, String value) {
        verify(exactly(1), postRequestedFor(urlPathMatching("/webhooks/.*"))
                .withHeader(header, equalTo(value)));
    }

    public void verifyWebhookCalledWithSignature() {
        verify(exactly(1), postRequestedFor(urlPathMatching("/webhooks/.*"))
                .withHeader("X-Webhook-Signature", matching("sha256=.*")));
    }

    public void verifyWebhookNotCalled() {
        verify(0, postRequestedFor(urlPathMatching("/webhooks/.*")));
    }
}
