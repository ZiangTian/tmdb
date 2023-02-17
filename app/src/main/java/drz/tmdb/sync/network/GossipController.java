package drz.tmdb.sync.network;

import drz.tmdb.sync.config.GossipConfig;
import drz.tmdb.sync.node.Node;
import drz.tmdb.sync.share.ReceiveDataArea;
import drz.tmdb.sync.share.SendInfo;
import drz.tmdb.sync.timeTest.SendTimeTest;
import drz.tmdb.sync.timeTest.TimeTest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;


public class GossipController {
    private final InetSocketAddress socketAddress;

    public SocketService socketService;

    //private Node currentNode = null;//当前节点

    //private Object[] currentNodes;//当前节点已知集群中的其他活跃节点的套接字地址集合

    //private ConcurrentHashMap<String,Node> nodes = new ConcurrentHashMap<>();

    //private volatile GossipRequest currentRequest = null;

    //private volatile boolean requestIsSent = false;
    private SendInfo sendInfo;

    private ReceiveDataArea receiveDataArea;

    private boolean stop = false;

    private ConcurrentLinkedQueue<GossipRequest> receivedRequestQueue = new ConcurrentLinkedQueue<>();//接收的请求队列

    private GossipConfig gossipConfig = null;

    private GossipUpdater onNewMember = null;

    private GossipUpdater onFailedMember = null;

    private GossipUpdater onRemovedMember = null;

    private GossipUpdater onRevivedMember = null;

    public GossipController(int sendInfoSize, int receiveDataAreaSize, InetSocketAddress socketAddress, GossipConfig gossipConfig) {
        this.socketAddress = socketAddress;
        this.gossipConfig = gossipConfig;

        this.socketService =new SocketService(socketAddress.getPort());
        //currentNode = self;

        sendInfo = new SendInfo(sendInfoSize);
        receiveDataArea = new ReceiveDataArea(receiveDataAreaSize);
        //currentNode = new Node(socketAddress,0,gossipConfig);
        //nodes.putIfAbsent(socketAddress.toString(),self);

    }

    /*public GossipController(InetSocketAddress sourceAddress, InetSocketAddress targetAddress, GossipConfig gossipConfig) {
        this(sourceAddress, gossipConfig);
        Node initialTarget = new Node(targetAddress, 0, gossipConfig);
        nodes.putIfAbsent(initialTarget.getNodeID(), initialTarget);
    }*/



    public ConcurrentLinkedQueue<GossipRequest> getReceivedRequestQueue() {
        return receivedRequestQueue;
    }

    public SendInfo getSendInfo() {
        return sendInfo;
    }

    public ReceiveDataArea getReceiveDataArea() {
        return receiveDataArea;
    }



    public void start(){

        startSendThread();
        startReceiveThread();
        //startFailureDetectionThread();
        //printNodes();

    }

    /*
     * 获取集群中所有活跃的节点的套接字地址
     * */
    /*public ArrayList<InetSocketAddress> getAliveNodes(){
        int nodeNumber = nodes.size();
        ArrayList<InetSocketAddress> aliveNodes = new ArrayList<>(nodeNumber);
        Node n = null;

        for(String key : nodes.keySet()){
            n=nodes.get(key);
            if(!n.isFailed()){
                InetSocketAddress inetSocketAddress = n.getSocketAddress();
                aliveNodes.add(inetSocketAddress);
            }
        }

        return aliveNodes;
    }*/

    /*
     * 获取集群中所有故障的节点的套接字地址
     * */
    /*public ArrayList<InetSocketAddress> getFailedNodes(){
        int nodeNumber = nodes.size();
        ArrayList<InetSocketAddress> failedNodes = new ArrayList<>(nodeNumber);
        Node n = null;

        for (String key : nodes.keySet()){
            n = nodes.get(key);
            n.check();
            if(n.isFailed()){
                InetSocketAddress inetSocketAddress = n.getSocketAddress();
                failedNodes.add(inetSocketAddress);
            }
        }

        return failedNodes;
    }*/

    /*public ArrayList<InetSocketAddress> getAllMembers() {

        int initialSize = nodes.size();
        ArrayList<InetSocketAddress> allMembers = new ArrayList<>(initialSize);

        for (String key : nodes.keySet()) {
            Node node = nodes.get(key);
            InetSocketAddress inetSocketAddress = node.getSocketAddress();
            allMembers.add(inetSocketAddress);
        }

        return allMembers;
    }*/

    public void stop(){
        stop = true;
    }

    public void setOnNewMember(GossipUpdater onNewMember) {
        this.onNewMember = onNewMember;
    }

    public void setOnFailedMember(GossipUpdater onFailedMember) {
        this.onFailedMember = onFailedMember;
    }

    public void setOnRemovedMember(GossipUpdater onRemovedMember) {
        this.onRemovedMember = onRemovedMember;
    }

    public void setOnRevivedMember(GossipUpdater onRevivedMember) {
        this.onRevivedMember = onRevivedMember;
    }



    private void startSendThread(){


        Thread sendThread = new Thread(() -> {
            while (!stop) {
                //GossipRequest currentRequest = sendInfo.getRequestToSend().poll();//取队首并移出队
                sendGossipRequestToOtherNodes(/*currentRequest*/);
                try {
                    Thread.sleep(gossipConfig.updateFrequency.toMillis());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "sendThreadMain");
        sendThread.start();
    }

    private void startReceiveThread(){
        Thread receiveThread = new Thread(() -> {
            while (!stop) {
                receiveOtherRequest();
            }
        }, "receiveThread");
        Thread receiveBroadcastThread = new Thread(() -> {
            while (!stop){
                receiveBroadRequest();
            }
        },"receiveBroadcastThread");

        receiveThread.start();
        receiveBroadcastThread.start();
    }

    /*private void startFailureDetectionThread(){
        new Thread(() -> {
            while(!stop){
                detectFailedNodes();
                try{
                    Thread.sleep(gossipConfig.failureDetectionFrequency.toMillis());
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }).start();
    }*/

    private int getRandomIndex(int size){
        Random random = new Random();
        int i = random.nextInt(size);
        return i;
    }

    private void sendGossipRequestToOtherNodes(/*GossipRequest gossipRequest*/){
        //currentNode.increaseVersion();
        GossipRequest gossipRequest;
        Object[] currentNodes;

        long l1;
        synchronized (sendInfo) {
            if (sendInfo.structureIsEmpty()) {
                try {
                    sendInfo.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            l1 = System.currentTimeMillis();
            gossipRequest = sendInfo.getRequestToSend().poll();//取队首并移出队
            currentNodes = sendInfo.getTargets().poll();//取队首并移出队列

            sendInfo.notifyAll();
        }

        List<InetSocketAddress> otherNodes = new ArrayList<>();
        //Object[] keys = nodes.keySet().toArray();

        if (currentNodes.length < gossipConfig.maxTransmitNode) {
            for (int i = 0; i < currentNodes.length; i++) {
                //String key = (String) currentNodes[i];
                InetSocketAddress key = (InetSocketAddress) currentNodes[i];

                if (!key/*.getAddress()*/.equals(gossipRequest.getSourceIPAddress()/*currentNode.getNodeID()*/)) {
                    otherNodes.add(key);
                }
            }
        } else {
            for (int i = 0; i < gossipConfig.maxTransmitNode; i++) {
                //boolean flag = false;
                while (true/*!flag*/) {

                    InetSocketAddress key = (InetSocketAddress) currentNodes[getRandomIndex(currentNodes.length)];
                    if (!key.equals(gossipRequest.getSourceIPAddress()) && !otherNodes.contains(key)/*currentNode.getNodeID()*/) {
                        otherNodes.add(key);
                        //flag = true;
                        break;
                    }
                }
            }
        }

        long l2 = System.currentTimeMillis();
        if(Node.isTest()) {
            TimeTest.putInCostTreeMap(gossipRequest.getRequestID(),"获取发送信息耗时",(l2-l1));
        }
        /*
         * 对每个需要发送的节点都会开启一个线程进行异步的传输
         * */
        try {
            int count = 0;//发送线程计数

            int batch_id = gossipRequest.batch_id;

            for (InetSocketAddress target : otherNodes) {
                gossipRequest.setTargetIPAddress(target);

                Thread thread = new Thread(() -> {
                        try {
                            socketService.sendGossipRequest(gossipRequest);
                        } catch (IOException e) {
                        e.printStackTrace();
                        }
                    }, "sendThread" + count);

                count++;
                System.out.println("当前执行的线程为：" + Thread.currentThread().getName());
                l1 = System.currentTimeMillis();
                thread.start();
                thread.join();
                l2 = System.currentTimeMillis();
                if(Node.isTest()) {
                    TimeTest.putInCostTreeMap(gossipRequest.getRequestID(),"发送线程"+count+"完成发送耗时",(l2-l1));
                }

                //Node.sendTimeTest.get(batch_id).setSendRequestTimeOnce(SendTimeTest.calculate(l,l1));
            }

            /*Node.sendTimeTest.setSendRequestAverageTime(timeSum / otherNodes.size());
            Node.sendTimeTest.setWriteObjectAverageTime(
                    Node.sendTimeTest.getWriteObjectTotalTime() / otherNodes.size());*/


        } catch (InterruptedException e) {
                e.printStackTrace();
        }

    }

    public void receiveOtherRequest(){
        Object newData = socketService.receiveData();

        if(newData != null){
            if(newData instanceof GossipRequest) {
                GossipRequest request = (GossipRequest) newData;
                //request.setReceiveTime(System.currentTimeMillis());

                System.out.println(Thread.currentThread().getName() + "：节点" + socketAddress.toString() + "接收到来自节点" + request.getSourceIPAddress().toString() + "的请求");
                //System.out.println(Thread.currentThread().getName() + "：该请求发送和传输所耗费的时间为：" + request.getTransportTimeMillis() + "ms");
                //receivedRequestQueue.add(request);
                synchronized (receiveDataArea.getReceivedRequestQueue()) {

                    if(receiveDataArea.requestQueueFull()){
                        try {
                            receiveDataArea.getReceivedRequestQueue().wait();
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }

                    }

                    receiveDataArea.getReceivedRequestQueue().add(request);

                    receiveDataArea.getReceivedRequestQueue().notifyAll();
                }
            }
            else if(newData instanceof Response){
                Response response = (Response) newData;
                System.out.println("成功收到来自"+response.getSource()+"的响应");

                synchronized (receiveDataArea.getReceivedResponseQueue()){
                    if (receiveDataArea.responseQueueFull()){
                        try {
                            receiveDataArea.getReceivedResponseQueue().wait();
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }

                    }

                    receiveDataArea.getReceivedResponseQueue().add(response);
                    receiveDataArea.getReceivedResponseQueue().notifyAll();
                }

            }
        }

        //if(nodes.get(newRequest.getSourceIPAddress().toString()) == null){
        //接收到新的节点发来的gossip请求，维护集群的信息，增加这个新的节点
            /*synchronized (nodes){
                newNode.setGossipConfig(gossipConfig);
                newNode.setLastUpdateTime();
                nodes.putIfAbsent(newNode.getNodeID(),newNode);
                if(onNewMember != null){
                    onNewMember.update(newNode.getSocketAddress());
                }
            }*/
        //nodes.putIfAbsent(newRequest.getSourceIPAddress().toString(),new Node(newRequest.getSourceIPAddress(),0,gossipConfig));
        //}
        //存在异常,existingNode可能为空
        //Node existingNode = nodes.get(newRequest.getSourceIPAddress().toString());
        //System.out.println("节点" + socketAddress.toString() + "成功接收到来自" + newRequest.getSourceIPAddress().toString() + "的向量时钟");
        //解析请求中的向量时钟进行更新
        //currentNode.getVectorClock(newRequest.getKey()).merge(newRequest.getVectorClock());
        //existingNode.updateVersion(newNode.getVersion());


        //接收到请求解析其中的向量时钟并执行算法进行判断是否要更新

    }


    public void receiveBroadRequest(){
        Object newData = socketService.receiveBroadcastData();

        if(newData != null){
            if(newData instanceof GossipRequest) {
                GossipRequest request = (GossipRequest) newData;

                //过滤掉本机自己接收到来自自己的广播请求
                if (request.getSourceIPAddress().equals(socketAddress)){
                    return;
                }

                //request.setReceiveTime(System.currentTimeMillis());
                Deviation.setRequestReceiveTime(System.currentTimeMillis());
                System.out.println("广播请求接收时刻为："+System.currentTimeMillis());


                System.out.println(Thread.currentThread().getName() + "：节点" + socketAddress.toString() + "接收到来自节点" + request.getSourceIPAddress().toString() + "的广播请求");
                //System.out.println(Thread.currentThread().getName() + "：该广播请求发送和传输所耗费的时间为：" + request.getTransportTimeMillis() + "ms");
                //receivedRequestQueue.add(request);
                synchronized (receiveDataArea.getReceivedRequestQueue()) {

                    if(receiveDataArea.requestQueueFull()){
                        try {
                            receiveDataArea.getReceivedRequestQueue().wait();
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }

                    }

                    receiveDataArea.getReceivedRequestQueue().add(request);

                    receiveDataArea.getReceivedRequestQueue().notifyAll();
                }
            }
            else if(newData instanceof Response){
                Response response = (Response) newData;

                //System.out.println("成功收到来自"+response.getSource()+"的响应");
                synchronized (receiveDataArea.getReceivedResponseQueue()){
                    if (receiveDataArea.responseQueueFull()){
                        try {
                            receiveDataArea.getReceivedResponseQueue().wait();
                        }catch (InterruptedException e){
                            e.printStackTrace();
                        }

                    }

                    receiveDataArea.getReceivedResponseQueue().add(response);
                    receiveDataArea.getReceivedResponseQueue().notifyAll();
                }

            }
        }


    }

    /*private void detectFailedNodes(){
        String[] keys = new String[nodes.size()];
        nodes.keySet().toArray(keys);

        for (String key : keys){
            Node node = nodes.get(key);
            boolean failed = node.isFailed();
            //存在异常，node可能为空
            node.check();

            //检查后发现不一致
            if(failed != node.isFailed()){
                if(node.isFailed()){
                    if(onFailedMember != null){
                        onFailedMember.update(node.getSocketAddress());
                    }
                    else{
                        if(onRevivedMember != null){
                            onRevivedMember.update(node.getSocketAddress());
                        }
                    }

                }
            }

            if (node.shouldCleanUp()){
                synchronized (nodes){
                    nodes.remove(key);
                    if(onRemovedMember != null){
                        onRemovedMember.update(node.getSocketAddress());
                    }
                }
            }
        }
    }*/

    /*private void printNodes(){
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ArrayList<InetSocketAddress> allAliveNodes = getAliveNodes();
            ArrayList<InetSocketAddress> allFailedNodes = getFailedNodes();
            Node node;

            for(InetSocketAddress i : allAliveNodes){
                node = nodes.get(i.toString());
                if(node != null){
                    System.out.println("节点状态: " + node.getHostName() + ":" + node.getPort() + "- 活跃");
                }
            }

            for(InetSocketAddress i : allFailedNodes){
                node = nodes.get(i.toString());
                if(node != null){
                    System.out.println("节点状态: " + node.getHostName() + ":" + node.getPort() + "- 故障");
                }
            }

            *//*getAliveNodes().forEach(node ->
                    System.out.println("节点状态: " + node.getHostName() + ":" + node.getPort() + "- 活跃"));

            getFailedNodes().forEach(node ->
                    System.out.println("节点状态: " + node.getHostName() + ":" + node.getPort() + "- 故障"));*//*
        }).start();
    }*/



}

