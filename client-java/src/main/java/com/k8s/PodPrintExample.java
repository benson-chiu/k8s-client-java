package com.k8s;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerManager;
import io.kubernetes.client.extended.controller.LeaderElectingController;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.event.EventType;
import io.kubernetes.client.extended.event.legacy.EventBroadcaster;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.extended.event.legacy.LegacyEventBroadcaster;
import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.resourcelock.EndpointsLock;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EventSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.CallGeneratorParams;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.util.Config;
import okhttp3.OkHttpClient;
import org.slf4j.LoggerFactory;

public class PodPrintExample {
    public static void main(String[] args) throws IOException {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.OFF);

        ApiClient apiClient = Config.defaultClient();
        Configuration.setDefaultApiClient(apiClient);
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);

        OkHttpClient httpClient =
            apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        apiClient.setHttpClient(httpClient);

        // instantiating an informer-factory, and there should be only one informer-factory
        // globally.
        SharedInformerFactory informerFactory = new SharedInformerFactory();
        // registering pod-informer into the informer-factory.
        SharedIndexInformer<V1Pod> podInformer =
            informerFactory.sharedIndexInformerFor(
                (CallGeneratorParams params) -> coreV1Api.listNamespacedPodCall(
                        "default",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        params.resourceVersion,
                        null,
                        params.timeoutSeconds,
                        params.watch,
                        null),
                V1Pod.class,
                V1PodList.class);
        informerFactory.startAllRegisteredInformers();

        EventBroadcaster eventBroadcaster = new LegacyEventBroadcaster(coreV1Api);

        // podReconciler prints pod information on events
        PodPrintingReconciler podReconciler =
            new PodPrintingReconciler(
                podInformer,
                eventBroadcaster.newRecorder(
                    new V1EventSource().component("pod-printing-controller")));

        // Use builder library to construct a default controller.
        Controller controller =
            ControllerBuilder.defaultBuilder(informerFactory)
                .watch((workQueue) -> ControllerBuilder.controllerWatchBuilder(V1Pod.class, workQueue)
//                    .withWorkQueueKeyFunc(
//                        (V1Pod pod) ->
//                            new Request(pod.getMetadata().getName())) // optional, default to
//                    .withOnAddFilter(
//                        (V1Pod createdPod) ->
//                            createdPod.getMetadata()
//                                      .getName()
//                                      .startsWith("docker-")) // optional, set onAdd filter
//                    .withOnUpdateFilter(
//                        (V1Pod oldPod, V1Pod newPod) ->
//                            newPod.getMetadata()
//                                  .getName()
//                                  .startsWith("docker-")) // optional, set onUpdate filter
//                    .withOnDeleteFilter(
//                        (V1Pod deletedPod, Boolean stateUnknown) ->
//                            deletedPod.getMetadata()
//                                      .getName()
//                                      .startsWith("docker-")) // optional, set onDelete filter
                                      .build())
                .withReconciler(podReconciler) // required, set the actual reconciler
                .withName("pod-printing-controller") // optional, set name for controller
                .withWorkerCount(4) // optional, set worker thread count
                .withReadyFunc(podInformer::hasSynced) // optional, only starts controller when the
                // cache has synced up
                .build();

        // Use builder library to manage one or multiple controllers.
        ControllerManager controllerManager =
            ControllerBuilder.controllerManagerBuilder(informerFactory)
                .addController(controller)
                .build();

        LeaderElectingController leaderElectingController =
            new LeaderElectingController(
                new LeaderElector(
                    new LeaderElectionConfig(
                        new EndpointsLock("kube-system", "leader-election", "foo"),
                        Duration.ofMillis(10000),
                        Duration.ofMillis(8000),
                        Duration.ofMillis(5000))),
                controllerManager);

        System.out.print("Everything is OK !!!\n");
        leaderElectingController.run();
    }

    static class PodPrintingReconciler implements Reconciler {

        private Lister<V1Pod> podLister;
        private EventRecorder eventRecorder;

        public PodPrintingReconciler(
                SharedIndexInformer<V1Pod> podInformer, EventRecorder recorder) {
            this.podLister = new Lister<>(podInformer.getIndexer());
            this.eventRecorder = recorder;
        }

        @Override
        public Result reconcile(Request request) {
            V1Pod pod = podLister.namespace("default").get(request.getName());
            System.out.println(request);
            if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
                System.out.println("triggered reconciling " + pod.getMetadata().getName());
                eventRecorder.event(
                    pod,
                    EventType.Normal,
                    "Print Pod",
                    "Successfully printed %s",
                    pod.getMetadata().getName());
            }
            return new Result(false);
        }
    }
}