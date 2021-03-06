package ru.apbondarev.http2;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private BottleneckData data;

    public static void main(String[] args) {
        log.info("[{}] Starting http2 server", Thread.currentThread().getName());
        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.getEventBusOptions().setClustered(false);
        runServer(
                "http2server/src/main/java/" + Server.class.getPackage().getName().replace(".", "/"),
                Server.class.getName(),
                vertxOptions,
                null
        );
    }

    @Override
    public void start() throws Exception {
        Image image = new Image(vertx, "coin.png");

        data = new BottleneckData(0, 1024 * 1024);

        Router router = Router.router(vertx);

        router.get("/").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "text/html")
                    .end(image.generateHTML(16));
        });

        router.get("/img").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "image/png")
                    .end(image.getData());
        });

        router.get("/img/:x/:y").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "image/png")
                    .end(image.getPixel(Integer.parseInt(ctx.pathParam("x")), Integer.parseInt(ctx.pathParam("y"))));
        });

        router.get("/hello").handler(timing("hello", ctx -> {
            HttpServerResponse response = ctx.response()
                    .putHeader("Content-Type", "text/plain");
            StringBuilder builder = new StringBuilder(50);
            builder.append("Hello");
            List<String> names = ctx.queryParam("name");
            if (names != null && !names.isEmpty()) {
                builder.append(", ");
                builder.append(String.join(", ", names));
            }
            builder.append('!');
            Buffer buffer = Buffer.buffer(builder.toString());
            response.end(buffer);
        }));

        router.get("/data/:key").blockingHandler(timing("get /data", ctx -> {
            int key = Integer.parseInt(ctx.pathParam("key"));
            String value = data.get(key);
            if (value == null) {
                ctx.response()
                        .setStatusCode(404)
                        .end();
            } else {
                Buffer buffer = Buffer.buffer(value);
                HttpServerResponse response = ctx.response()
                        .putHeader("Content-Type", "text/plain");
                response.end(buffer);
            }
        }));

        router.post("/data/:key").blockingHandler(timing("post /data", ctx -> {
            int key = Integer.parseInt(ctx.pathParam("key"));
            String value = ctx.getBodyAsString();
            data.put(key, value);
            HttpServerResponse response = ctx.response()
                    .putHeader("Content-Type", "text/plain");
            response.end();
        }));

        vertx.createHttpServer(
                new HttpServerOptions()
                        .setSsl(true)
                        .setUseAlpn(true)
                        .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert.pem"))).requestHandler(router)
                .listen(8443);
    }

    private static Handler<RoutingContext> timing(String statName, Handler<RoutingContext> handler) {
        return ctx -> {
            long timeStart = System.nanoTime();
            handler.handle(ctx);
            long timeEnd = System.nanoTime();
            log.info("[{}] {} {} µs", Thread.currentThread().getName(), statName, TimeUnit.NANOSECONDS.toMicros(timeEnd - timeStart));
        };
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
        if (options.getEventBusOptions().isClustered()) {
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
