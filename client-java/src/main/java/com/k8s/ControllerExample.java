package com.k8s;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.k8s.crds.V1alpha1AtCrd;
import com.k8s.models.V1alpha1At;
import com.k8s.models.V1alpha1AtList;
import com.k8s.models.V1alpha1AtSpec;
import com.k8s.models.V1alpha1AtStatus;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerManager;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.event.EventType;
import io.kubernetes.client.extended.event.legacy.EventBroadcaster;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.extended.event.legacy.LegacyEventBroadcaster;
import io.kubernetes.client.extended.workqueue.DefaultRateLimitingQueue;
import io.kubernetes.client.extended.workqueue.RateLimitingQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Caches;
import io.kubernetes.client.informer.cache.DeltaFIFO;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.ApiextensionsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.CallGeneratorParams;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.util.Config;
import okhttp3.OkHttpClient;
import org.slf4j.LoggerFactory;

public class ControllerExample {
    static final String CONTROLLER_NAME = "cnat-controller";

    // SuccessSynced is used as part of the Event 'reason' when a Foo is synced
    static final String SUCCESS_SYNCED = "Synced";

    // MessageResourceSynced is the message used for an Event fired when a Foo is synced successfully
    static final String MESSAGE_RESOURCE_SYNCED = "At synced successfully";

    public static void main(String[] args) throws IOException {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.OFF);

        ApiClient apiClient = Config.defaultClient();
        Configuration.setDefaultApiClient(apiClient);
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        ApiextensionsV1Api extensionsV1Api = new ApiextensionsV1Api(apiClient);
        CustomObjectsApi customObjectsApi = new CustomObjectsApi(apiClient);

        OkHttpClient httpClient =
                apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        apiClient.setHttpClient(httpClient);

        // Create At CRD from extensions API
        try {
            extensionsV1Api.createCustomResourceDefinition(
                new V1alpha1AtCrd().getAtCrdBody(),
                null,
                null,
                null
            );
            System.out.println("CRD 'ats.cnat.programming-kubernetes.info' created successfully");
        } catch (ApiException e) {
            System.err.println("Exception when calling ApiextensionsV1Api#createCustomResourceDefinition");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }

        // The CRD needs to be deleted before the controller shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Delete At CRD from extensions API
            try {
                extensionsV1Api.deleteCustomResourceDefinition(
                    "ats.cnat.programming-kubernetes.info",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                );
                System.out.println("CRD 'ats.cnat.programming-kubernetes.info' deleted successfully");
            } catch (ApiException e) {
                System.err.println("Exception when calling ApiextensionsV1Api#deleteCustomResourceDefinition");
                System.err.println("Status code: " + e.getCode());
                System.err.println("Reason: " + e.getResponseBody());
                System.err.println("Response headers: " + e.getResponseHeaders());
                e.printStackTrace();
            }
            System.out.println("Controller shutdown !!!");
        }));

        // Create At CR from custom objects API
        V1ObjectMeta metadata = new V1ObjectMeta().name("example-at-java");
        V1alpha1AtSpec spec = new V1alpha1AtSpec().command("echo YAY JAVA")
                                                  .schedule("2023-06-28T18:00:00Z");

        V1alpha1At alpha1At = new V1alpha1At().kind("At")
                                              .apiVersion("cnat.programming-kubernetes.info/v1alpha1")
                                              .metadata(metadata)
                                              .spec(spec);

        try {
            customObjectsApi.createNamespacedCustomObject(
                "cnat.programming-kubernetes.info",
                "v1alpha1",
                "default",
                "ats",
                alpha1At,
                null,
                null,
                null
            );
            System.out.println("CR 'example-at-java' created successfully");
        } catch (ApiException e) {
            System.err.println("Exception when calling CustomObjectsApi#createNamespacedCustomObject");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }

        /////////////////////////////////////////////////////////////////////////////////////////////
        // instantiating an informer-factory, and there should be only one informer-factory globally.
        SharedInformerFactory informerFactory = new SharedInformerFactory();

        // registering pod-informer into the informer-factory.
        SharedIndexInformer<V1Pod> podInformer =
            informerFactory.sharedIndexInformerFor(
                (CallGeneratorParams params) -> coreV1Api.listPodForAllNamespacesCall(
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

        // registering at-informer into the informer-factory.
        SharedIndexInformer<V1alpha1At> atInformer =
            informerFactory.sharedIndexInformerFor(
                (CallGeneratorParams params) -> customObjectsApi.listNamespacedCustomObjectCall(
                    "cnat.programming-kubernetes.info",
                    "v1alpha1",
                    "default",
                    "ats",
                    null,
                    null,
                    null,
                    null,
                    null,
                    params.resourceVersion,
                    params.timeoutSeconds,
                    params.watch,
                    null),
                V1alpha1At.class,
                V1alpha1AtList.class);

        informerFactory.startAllRegisteredInformers();

        EventBroadcaster eventBroadcaster = new LegacyEventBroadcaster(coreV1Api);
        DefaultRateLimitingQueue<Request> workqueue = new DefaultRateLimitingQueue<>();

        // Controller reconciler prints node information on events
        ControllerReconciler controllerReconciler = new ControllerReconciler(
            coreV1Api, customObjectsApi, workqueue,
            podInformer, atInformer, eventBroadcaster.newRecorder(
                new V1EventSource().host("localhost").component(CONTROLLER_NAME)));

        podInformer.addEventHandler(
            new ResourceEventHandler<V1Pod>() {
                @Override
                public void onAdd(V1Pod pod) {
                    controllerReconciler.enqueuePod(pod);
                    System.out.printf("%s pod added!\n", pod.getMetadata().getName());
                }

                @Override
                public void onUpdate(V1Pod oldPod, V1Pod newPod) {
                    controllerReconciler.enqueuePod(newPod);
                    System.out.printf(
                        "%s => %s pod updated!\n",
                        oldPod.getMetadata().getName(), newPod.getMetadata().getName());
                }

                @Override
                public void onDelete(V1Pod pod, boolean deletedFinalStateUnknown) {
                    System.out.printf("%s pod deleted!\n", pod.getMetadata().getName());
                }
            }
        );

        atInformer.addEventHandler(
            new ResourceEventHandler<V1alpha1At>() {
                @Override
                public void onAdd(V1alpha1At at) {
                    controllerReconciler.enqueueAt(at);
                    System.out.printf("%s at added!\n", at.getMetadata().getName());
                }

                @Override
                public void onUpdate(V1alpha1At oldAt, V1alpha1At newAt) {
                    controllerReconciler.enqueueAt(newAt);
                    System.out.printf(
                        "%s => %s at updated!\n",
                        oldAt.getMetadata().getName(), newAt.getMetadata().getName());
                }

                @Override
                public void onDelete(V1alpha1At at, boolean deletedFinalStateUnknown) {
                    System.out.printf("%s at deleted!\n", at.getMetadata().getName());
                }
            }
        );

        // Use builder library to construct a default controller.
        Controller controller =
            ControllerBuilder.defaultBuilder(informerFactory)
                .withReconciler(controllerReconciler) // required, set the actual reconciler
                .withWorkQueue(workqueue)
                .withName(CONTROLLER_NAME) // optional, set name for controller
                .withWorkerCount(1) // optional, set worker thread count
                // optional, only starts controller when the cache has synced up
                .withReadyFunc(podInformer::hasSynced)
                .withReadyFunc(atInformer::hasSynced)
                .build();

        // Use builder library to manage one or multiple controllers.
        ControllerManager controllerManager =
            ControllerBuilder.controllerManagerBuilder(informerFactory)
                             .addController(controller)
                             .build();
        controllerManager.run();
    }

    static class ControllerReconciler implements Reconciler {
        protected CoreV1Api coreV1Api;
        protected CustomObjectsApi customObjectsApi;
        protected Lister<V1Pod> podLister;
        protected Lister<V1alpha1At> atLister;

        protected RateLimitingQueue<Request> workqueue;
        protected EventRecorder recorder;

        public ControllerReconciler(CoreV1Api coreV1Api,
                                    CustomObjectsApi customObjectsApi,
                                    DefaultRateLimitingQueue<Request> workqueue,
                                    SharedIndexInformer<V1Pod> podInformer,
                                    SharedIndexInformer<V1alpha1At> atInformer,
                                    EventRecorder recorder) {
            this.coreV1Api = coreV1Api;
            this.customObjectsApi = customObjectsApi;
            this.atLister = new Lister<>(atInformer.getIndexer());
            this.podLister = new Lister<>(podInformer.getIndexer());
            this.workqueue = workqueue;
            this.recorder = recorder;
        }

        @Override
        public Result reconcile(Request key) {
            System.out.printf("=== Reconciling At %s\n", key);

            String namespace = key.getNamespace();
            String name = key.getName();
            if (namespace == null || name == null) {
                System.out.printf("invalid resource key: %s\n", key);
                return new Result(false, Duration.ZERO);
            }

            V1alpha1At original = atLister.namespace(namespace).get(name);
            if (original == null) {
                System.out.printf("at '%s' in work queue no longer exists\n", key);
                return new Result(false, Duration.ZERO);
            }

            if (original.getStatus() == null || original.getStatus().getPhase() == null) {
                original.setStatus(new V1alpha1AtStatus().phase(""));
            }

            // Clone because the original object is owned by the lister.
            // Deep copy
            String json = new JSON().serialize(original);
            V1alpha1At instance = new JSON().deserialize(json, V1alpha1At.class);

            // If no phase set, default to pending (the initial phase):
            if (instance.getStatus().getPhase().equals("")) {
                instance.setStatus(new V1alpha1AtStatus().phase("PENDING"));
            }

            // Now let's make the main case distinction: implementing
            // the state diagram PENDING -> RUNNING -> DONE
            switch (instance.getStatus().getPhase()) {
                case "PENDING":
                    System.out.printf("instance %s: phase=PENDING\n", key);
                    // As long as we haven't executed the command yet,  we need to check if it's time already to act:
                    assert instance.getSpec() != null;
                    System.out.printf("instance %s: checking schedule %s\n", key, instance.getSpec().getSchedule());
                    // Check if it's already time to execute the command with a tolerance of 2 seconds:
                    Duration d = timeUntilSchedule(instance.getSpec().getSchedule());
                    System.out.printf("instance %s: schedule parsing done: diff=%s\n", key, d);
                    if (d.getSeconds() > 0) {
                        // Not yet time to execute the command, wait until the scheduled time
                        return new Result(true, d);
                    }

                    System.out.printf("instance %s: it's time! Ready to execute: %s\n", key, instance.getSpec().getCommand());
                    instance.setStatus(new V1alpha1AtStatus().phase("RUNNING"));
                    break;
                case "RUNNING":
                    System.out.printf("instance %s: phase: RUNNING\n", key);

                    V1Pod pod = newPodForCR(instance);

                    // Set At instance as the owner and controller
                    V1OwnerReference v1OwnerReference = new V1OwnerReference();
                    v1OwnerReference.kind(instance.getKind())
                                    .name(instance.getMetadata().getName())
                                    .blockOwnerDeletion(true)
                                    .controller(true)
                                    .uid(instance.getMetadata().getUid())
                                    .apiVersion(instance.getApiVersion());

                    // Establish the association between pod and At
                    V1ObjectMeta podMetadata = pod.getMetadata();
                    podMetadata.addOwnerReferencesItem(v1OwnerReference);
                    pod.setMetadata(podMetadata);

                    // Try to see if the pod already exists and if not
                    // (which we expect) then create a one-shot pod as per spec:
                    V1Pod found = podLister.namespace(pod.getMetadata().getNamespace()).get(pod.getMetadata().getName());
                    if (found == null) {
                        System.out.printf("at '%s' in work queue no longer exists\n", key);
                        try {
                            coreV1Api.createNamespacedPod("default", pod, null, null, null);
                        } catch (ApiException e) {
                            System.out.println(e.getMessage());
                            return new Result(false, Duration.ZERO);
                        }
                        System.out.printf("instance %s: pod launched: name=%s\n", key, pod.getMetadata().getName());
                    } else if (found.getStatus().getPhase().equals("Failed") ||
                               found.getStatus().getPhase().equals("Succeeded")) {
                        System.out.printf("instance %s: container terminated: reason=%s message=%s\n", key,
                            found.getStatus().getReason(), found.getStatus().getMessage());
                        instance.setStatus(new V1alpha1AtStatus().phase("DONE"));
                    } else {
                        // don't requeue because it will happen automatically when the pod status changes
                        return new Result(false, Duration.ZERO);
                    }
                    break;
                case "DONE":
                    System.out.printf("instance %s: phase: DONE\n", key);
                    return new Result(false, Duration.ZERO);
                default:
                    System.out.printf("instance %s: NOP\n", key);
                    return new Result(false, Duration.ZERO);
            }

            if (!original.equals(instance)) {
                try {
                    customObjectsApi.replaceNamespacedCustomObject(
                            "cnat.programming-kubernetes.info",
                            "v1alpha1",
                            instance.getMetadata().getNamespace(),
                            "ats",
                            instance.getMetadata().getName(),
                            instance,
                            null,
                            null
                    );
                } catch (ApiException e) {
                    System.out.println(e.getMessage());
                    return new Result(false, Duration.ZERO);
                }
            }

            recorder.event(instance, EventType.Normal, SUCCESS_SYNCED, MESSAGE_RESOURCE_SYNCED);
            return new Result(false, Duration.ZERO);
        }

        public void enqueueAt(KubernetesObject obj) {
            String key = Caches.metaNamespaceKeyFunc(obj);
            if (key.contains("/")) {
                String[] tokens = key.split("/");
                String namespace = tokens[0];
                String name = tokens[1];
                workqueue.add(new Request(namespace, name));
            } else {
                workqueue.add(new Request(key));
            }
        }

        public void enqueuePod(KubernetesObject obj) {
            V1Pod pod;
            if (!(obj instanceof V1Pod)) {
                if (!(obj instanceof DeltaFIFO.DeletedFinalStateUnknown)) {
                    System.out.println("error decoding pod, invalid type");
                    return;
                }
                pod = ((DeltaFIFO.DeletedFinalStateUnknown<V1Pod>) obj).getObj();
                if (pod == null) {
                    System.out.println("error decoding pod tombstone, invalid type");
                    return;
                }
                System.out.printf("Recovered deleted pod '%s' from tombstone\n", pod.getMetadata().getName());
            } else {
                pod = (V1Pod) obj;
            }

            V1ObjectMeta meta = pod.getMetadata();
            if (meta != null && meta.getOwnerReferences() != null) {
                meta.getOwnerReferences().forEach(ownerRef -> {
                    if (!ownerRef.getKind().equals("At")) {
                        return;
                    }

                    V1alpha1At at = atLister.namespace(pod.getMetadata().getNamespace()).get(ownerRef.getName());
                    if (at == null) {
                        System.out.printf("ignoring orphaned pod '%s' of At '%s'\n",
                            pod.getMetadata().getSelfLink(), ownerRef.getName());
                        return;
                    }

                    assert at.getMetadata() != null;
                    if (at.getMetadata() != null) {
                        System.out.printf("enqueuing At %s/%s because pod changed\n",
                            at.getMetadata().getNamespace(), at.getMetadata().getName());
                    } else {
                        System.out.println("enqueuing At because pod changed");
                    }
                    enqueueAt(at);
                });
            }
        }

        public Duration timeUntilSchedule(String schedule) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            Date now = new Date();
            Date s;

            try {
                s = dateFormat.parse(schedule);
            } catch (ParseException e) {
                System.out.println("schedule parsing failed: " + e.getMessage());
                // Error reading the schedule - requeue the request:
                return Duration.ZERO;
            }
            return Duration.ofSeconds((s.getTime() - now.getTime()) / 1000);
        }

        public V1Pod newPodForCR(V1alpha1At cr) {
            assert cr.getMetadata() != null;
            assert cr.getSpec() != null;
            assert cr.getSpec().getCommand() != null;

            Map<String, String> labels = new HashMap<>();
            labels.put("app", cr.getMetadata().getName());

            return new V1Pod()
                .metadata(new V1ObjectMeta()
                    .name(cr.getMetadata().getName() + "-pod")
                    .namespace(cr.getMetadata().getNamespace())
                    .labels(labels))
                .spec(new V1PodSpec()
                    .containers(Collections.singletonList(new V1Container()
                        .name("busybox")
                        .image("busybox")
                        .command(Arrays.asList(cr.getSpec().getCommand().split(" ")))))
                    .restartPolicy("OnFailure"));
        }
    }
}
