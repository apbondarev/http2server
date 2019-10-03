package ru.apbondarev.http2;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;

public class Server extends AbstractVerticle {

    public static void main(String[] args) {
        runServer(
                "http2server/src/main/java/" + Server.class.getPackage().getName().replace(".", "/"),
                Server.class.getName(),
                new VertxOptions().setClustered(false),
                null
        );
    }

    @Override
    public void start() throws Exception {
        Image image = new Image(vertx, "coin.png");

        Router router = Router.router(vertx);

        router.get("/").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "text/html")
                    .end(image.generateHTML(16));
        });

        router.get("/img/:x/:y").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "image/png")
                    .end(image.getPixel(Integer.parseInt(ctx.pathParam("x")), Integer.parseInt(ctx.pathParam("y"))));
        });

        vertx.createHttpServer(
                new HttpServerOptions()
                        .setSsl(true)
                        .setUseAlpn(true)
                        .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert.pem"))).requestHandler(router)
                .listen(8443);
    }

    private static void runServer(
            String currentDir,
            String verticleID,
            VertxOptions options,
            DeploymentOptions deploymentOptions
    ) {
        if (options == null) {
            // Default parameter
            options = new VertxOptions();
        }
        // Smart cwd detection

        // Based on the current directory (.) and the desired directory (exampleDir), we try to compute the vertx.cwd
        // directory:
        try {
            // We need to use the canonical file. Without the file name is .
            File current = new File(".").getCanonicalFile();
            if (currentDir.startsWith(current.getName()) && !currentDir.equals(current.getName())) {
                currentDir = currentDir.substring(current.getName().length() + 1);
            }
        } catch (IOException e) {
            // Ignore it.
        }

        System.setProperty("vertx.cwd", currentDir);
        Consumer<Vertx> runner = vertx -> {
            try {
                if (deploymentOptions != null) {
                    vertx.deployVerticle(verticleID, deploymentOptions);
                } else {
                    vertx.deployVerticle(verticleID);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };
        if (options.isClustered()) {
            Vertx.clusteredVertx(options, res -> {
                if (res.succeeded()) {
                    Vertx vertx = res.result();
                    runner.accept(vertx);
                } else {
                    res.cause().printStackTrace();
                }
            });
        } else {
            Vertx vertx = Vertx.vertx(options);
            runner.accept(vertx);
        }
    }
}
