# 使用client-java設置k8s controller
- [定義所需的K8s API object](#定義所需的K8s-API-object)
- [CRD的新增與刪除](#CRD的新增與刪除)
- [建構CRD API model以及新增CR](#建構CRD-API-model以及新增CR)
- [生成Informer並持續監聽Event](#生成Informer並持續監聽Event)
- [建立Controller object並執行](#建立Controller-object並執行)
- [建立Controller reconciler規則](#建立Controller-reconciler規則)
- [PENDING state](#PENDING-state)
- [RUNNING state](#RUNNING-state)
- [測試client-java controller](#測試client\-java-controller) 

## 定義所需的K8s API object
以下將定義client-java API初始化以及所需K8s resource對應的API
![][api]<br>
coreV1Api: 針對Node、Pod等相關核心資源的CRUD操作API<br>
extensionsV1Api: 針對CRD資源的CRUD操作API<br>
customObjectsApi: 針對特定CRD內部CR的CRUD操作API

**註記**<br>
**CRD**: Custom Resource Definition<br>
**CR**: Custom Resource

以下所介紹的範例是根據Charles所寫之client-go controller改寫的client-java版本。<br>
請參考: https://github.com/charleschou56/kubesphere/blob/controller-dev-k8s/pkg/controller/cnat/cnat_controller.go

## CRD的新增與刪除
啟動Controller的同時，使用extensionsV1Api事先新增指定的CRD
以供後續在這個CRD中加入CR。

※ 其中[V1alpha1AtCrd](https://github.com/benson-chiu/k8s-client-java/blob/master/client-java/src/main/java/com/k8s/crds/V1alpha1AtCrd.java "V1alpha1AtCrd")內容為定義CRD [yaml檔案](https://github.com/charleschou56/kubesphere/blob/controller-dev-k8s/config/crds/cnat.programming-kubernetes.info_ats.yaml "yaml檔案")轉換成client-java body的實例。
![][create-crd]

另外在Controller關閉前，使用extensionsV1Api來將指定CRD進行刪除動作，這個操作會將裡面的CR一併刪除。
![][delete-crd]

## 建構CRD API model以及新增CR
可參考client-java[官方文檔](https://github.com/kubernetes-client/java/blob/master/docs/generate-model-from-third-party-resources.md "官方文檔")，該文檔提供兩種方式來自動生成CRD API model，推薦使用官方Github action遠端生成，步驟如下:

1. 準備CRD定義yaml檔案 (個人事先將yaml檔案上傳至Github gist)

2. 到kubernetes-client/java的官方[Github](https://github.com/kubernetes-client/java/blob/master/docs/generate-model-from-third-party-resources.md "Github")，fork一份到自己的Github帳號
3. 點選Action -> CRD Java Model Generate -> Run workflow，並填入該定義CRD yaml檔案位置(這裡我填入Github gist上yaml檔案的HTTP URL)以及client-java controller main function的package name
![][github-action]

4. 等待workflow執行完成後，點選該worflow詳細資訊->Summary，在下方的Artifacts中即可看到產生完成的CRD API model class的壓縮檔
![][github-action-summary]

generated-java-crd-model壓縮檔裡面內容如下:<br>
![][crd-model]

有了上面這些CRD model class<br>
我們將可以在controller中操作該CRD中任何CR的物件，透過customObjectsApi在指定CRD中新增CR<br>
![][create-cr]

## 生成Informer並持續監聽Event
我們透過SharedInformerFactory來生成pod informer以及上個章節產生CRD API的informer (at informer)，其中這兩個informer中的params.watch皆為true，表示informer將會持續監聽pod以及At CR這兩種resource的event。
![][create-informer]

在informer中，我們可以修改接收到特定resource新增、更新和刪除event之後要處理的動作。
<br>controllerReconciler這個object稍後將會深入介紹。<br>
![][pod-informer-event]
![][cr-informer-event]

## 建立Controller object並執行
使用client-java內建的builder library來生成controller物件，需要指定上節提到所生成的informer集合以及reconciler。

另外可以指定workqueue物件、controller名稱以及worker(處理workqueue的主要角色)執行緒的數量，也可以指定需要等待哪些informer啟用並同步完成後才可啟動controller。

最後將生成的controller物件加到controller manager object中並執行。
![][controller]

## 建立Controller reconciler規則
我們需要建立ControllerReconciler這個class，來實例client-java內建reconciler的interface，該class的內部變數以及所帶入的參數可根據開發者Controller設計調諧resource需求來自行定義。

任何與reconciler相關的function定義建議都放在這個class中。
![][reconciler-class]

其中最重要的就是reconcile這個function，當workqueue中存在resource object key時就會觸發這個function來執行各項k8s resource object處理程序，例如讀取CR內部spec中的data、改變CR status或是建立Pod等等。

reconcile function會回傳一個Result物件，該物件有兩項參數，其中一個是boolean型態的requeue，另個則是java.time.Duration型態的requeueAfter。當requeue為true時，經過requeueAfter所表示的時間後將會再次觸發reconcile function，反之requeue為false時則不再觸發。

該function功能等同於使用client-go controller runtime架構中的syncHandler function。
![][reconciler-function]

這裡reconcile功能與Charles的client-go controller是等效的，程式會有3個狀態:<br>
<br>時間還沒到: PENDING
<br>時間到: RUNNING, 建立Pod並執行CR spec裡面的command內容
<br>Pod已建立: DONE

reconcile裡拿到at這個CR之後會將裡面的phase狀態初始化為”PENDING”

## PENDING state
透過timeUntilSchedule這個function，可以取得CR spec裡面的schedule時間字串與現在的時間差。
![][timeUntilSchedule]

時間變數d透過timeUntilSchedule取得時間差，當d > 0表示時間還沒到，這裡回傳new Result(true, d)，表示在d的時間後requeue, 所以過了d的時間後將會再執行一次reconcile function。
![][pending-state]

## RUNNING state
透過newPodForCR這個function根據CR裡面的內容來建立Pod。
![][create-pod]

進入到RUNNING狀態後，建立完的Pod將會與CR做OwnerReference關聯。之後若這個CR被移除，所關聯的Pod也會一併刪除。<br>
![][running-state]

## 測試client-java controller
使用maven安裝client-java依賴的module(參考此pom.xml檔案)並編譯client-java controller<br>
`mvn clean install` <br>
編譯完成後執行以下command來啟動client-java controller <br>
`mvn exec:java -D exec.mainClass=com.k8s.ControllerExample -D exec.cleanupDaemonThreads=false` <br>

![][run-controller]

可透過kubectl中看到CRD、CR以及關聯的Pod以建立完成。<br>
![][kubectl]


[api]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/api.jpg

[controller]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/controller.jpg

[cr-informer-event]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/cr-informer-event.jpg

[crd-model]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/crd-model.jpg

[create-cr]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/create-cr.jpg

[create-crd]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/create-crd.jpg

[create-informer]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/create-informer.jpg

[create-pod]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/create-pod.jpg

[delete-crd]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/delete-crd.jpg

[github-action-summary]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/github-action-summary.jpg

[github-action]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/github-action.jpg

[kubectl]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/kubectl.jpg

[pending-state]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/pending-state.jpg

[pod-informer-event]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/pod-informer-event.jpg

[reconciler-class]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/reconciler-class.jpg

[reconciler-function]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/reconciler-function.jpg

[run-controller]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/run-controller.jpg

[running-state]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/running-state.jpg

[timeUntilSchedule]: https://raw.githubusercontent.com/benson-chiu/k8s-client-java/master/client-java/images/timeUntilSchedule.jpg
