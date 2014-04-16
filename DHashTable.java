package src;

/****** SALSA LANGUAGE IMPORTS ******/
import salsa_lite.common.DeepCopy;
import salsa_lite.runtime.MobileActorRegistry;
import salsa_lite.runtime.wwc.NameServer;
import salsa_lite.runtime.Hashing;
import salsa_lite.runtime.Acknowledgement;
import salsa_lite.runtime.SynchronousMailboxStage;
import salsa_lite.runtime.Actor;
import salsa_lite.runtime.Message;
import salsa_lite.runtime.RemoteActor;
import salsa_lite.runtime.MobileActor;
import salsa_lite.runtime.StageService;
import salsa_lite.runtime.TransportService;
import salsa_lite.runtime.language.Director;
import salsa_lite.runtime.language.JoinDirector;
import salsa_lite.runtime.language.MessageDirector;
import salsa_lite.runtime.language.ContinuationDirector;
import salsa_lite.runtime.language.TokenDirector;

import salsa_lite.runtime.language.exceptions.RemoteMessageException;
import salsa_lite.runtime.language.exceptions.TokenPassException;
import salsa_lite.runtime.language.exceptions.MessageHandlerNotFoundException;
import salsa_lite.runtime.language.exceptions.ConstructorNotFoundException;

import salsa_lite.runtime.wwc.OutgoingTheaterConnection;

/****** END SALSA LANGUAGE IMPORTS ******/

import salsa_lite.runtime.language.JoinDirector;
import salsa_lite.runtime.io.StandardOutput;
import salsa_lite.runtime.TransportService;
import salsa_lite.common.HashCodeBuilder;
import javasrc.ChordKey;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Scanner;
import java.io.FileNotFoundException;
import java.io.File;

public class DHashTable extends MobileActor implements java.io.Serializable {

    public Object writeReplace() throws java.io.ObjectStreamException {
        return new SerializedDHashTable( this.getName(), this.getNameServer(), this.getLastKnownHost(), this.getLastKnownPort());
    }

    public static class SerializedDHashTable implements java.io.Serializable {
        String name;
        String lastKnownHost;
        int lastKnownPort;

        NameServer nameserver;

        SerializedDHashTable(String name, NameServer nameserver, String lastKnownHost, int lastKnownPort) { this.name = name; this.nameserver = nameserver; this.lastKnownHost = lastKnownHost; this.lastKnownPort = lastKnownPort; }

        public Object readResolve() throws java.io.ObjectStreamException {
            int hashCode = Hashing.getHashCodeFor(name, nameserver.getName(), nameserver.getHost(), nameserver.getPort());

                synchronized (MobileActorRegistry.getStateLock(hashCode)) {
                    Actor entry = MobileActorRegistry.getStateEntry(hashCode);
                    if (entry == null) {
                        MobileActorRegistry.addStateEntry(hashCode, TransportService.getSocket(lastKnownHost, lastKnownPort));
                    }
                }

            synchronized (MobileActorRegistry.getReferenceLock(hashCode)) {
                DHashTable actor = (DHashTable)MobileActorRegistry.getReferenceEntry(hashCode);
                if (actor == null) {
                    DHashTable remoteReference = new DHashTable(name, nameserver, lastKnownHost, lastKnownPort);
                    MobileActorRegistry.addReferenceEntry(hashCode, remoteReference);
                    return remoteReference;
                } else {
                    return actor;
                }
            }
        }
    }

    public Object invokeMessage(int messageId, Object[] arguments) throws RemoteMessageException, TokenPassException, MessageHandlerNotFoundException {
        Object entry;
        int hashCode = hashCode();
        synchronized (MobileActorRegistry.getStateLock(hashCode)) {
            entry = MobileActorRegistry.getStateEntry(hashCode);
        }
        if (entry instanceof State) {
            return ((State)entry).invokeMessage(messageId, arguments);
        } else {
            StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, ((OutgoingTheaterConnection)entry), 2 /*send*/, new Object[]{this.getStage().message}));
            throw new RemoteMessageException();
        }
    }

    public void invokeConstructor(int messageId, Object[] arguments) throws RemoteMessageException, ConstructorNotFoundException {
        Object entry;
        int hashCode = hashCode();
        synchronized (MobileActorRegistry.getStateLock(hashCode)) {
            entry = MobileActorRegistry.getStateEntry(hashCode);
        }
        if (entry instanceof State) {
            ((State)entry).invokeConstructor(messageId, arguments);
        } else {
            StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, ((OutgoingTheaterConnection)entry), 2 /*send*/, new Object[]{this.getStage().message}));
            throw new RemoteMessageException();
        }
    }


    public DHashTable(String name, NameServer nameserver) { super(name, nameserver); }
    public DHashTable(String name, NameServer nameserver, int stage_id) { super(name, nameserver, stage_id); }
    public DHashTable(String name, NameServer nameserver, String lastKnownHost, int lastKnownPort) { super(name, nameserver, lastKnownHost, lastKnownPort); }

    public DHashTable(String name, NameServer nameserver, String lastKnownHost, int lastKnownPort, int stage_id) { super(name, nameserver, lastKnownHost, lastKnownPort, stage_id); }

    public static void main(String[] arguments) {
        TransportService.initialize();
        String name = System.getProperty("called");
        String nameserver_info = System.getProperty("using");
        if (name == null || nameserver_info == null) {
            System.err.println("Error starting DHashTable: to run a mobile actor you must specify a name with the '-Dcalled=<name>' system property and a namesever with the '-Dusing=\"<nameserver_host>:<nameserver_port>/<nameserver_name>\"' system property.");
            System.err.println("usage: (port is optional and 4040 by default)");
            System.err.println("	java -Dcalled=\"<name>\" [-Dport=4040] src.DHashTable");
            System.exit(0);
        }
        try {
            int colon_index = nameserver_info.indexOf(':');
            int slash_index = nameserver_info.indexOf('/');
            String nameserver_host = nameserver_info.substring(0,colon_index);
            int nameserver_port = Integer.parseInt(nameserver_info.substring(colon_index + 1, slash_index));
            String nameserver_name = nameserver_info.substring(slash_index + 1, nameserver_info.length());
            DHashTable.construct(2, new Object[]{arguments}, name, NameServer.getRemoteReference(nameserver_name, nameserver_host, nameserver_port));
        } catch (Exception e) {
            System.err.println("Error in format of -Dusing system property, needs to be 'nameserver_host:nameserver_port/nameserver_name'.");
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static DHashTable construct(int constructor_id, Object[] arguments, String name, NameServer nameserver) {
        DHashTable actor = new DHashTable(name, nameserver);
        State state = new State(name, nameserver, actor.getStageId());

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        StageService.sendMessage(new Message(Message.CONSTRUCT_MESSAGE, actor, constructor_id, arguments));
        return actor;
    }

    public static DHashTable construct(int constructor_id, Object[] arguments, String name, NameServer nameserver, int target_stage_id) {
        DHashTable actor = new DHashTable(name, nameserver, target_stage_id);
        State state = new State(name, nameserver, target_stage_id);

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        actor.getStage().putMessageInMailbox(new Message(Message.CONSTRUCT_MESSAGE, actor, constructor_id, arguments));
        return actor;
    }


    public static TokenDirector construct(int constructor_id, Object[] arguments, int[] token_positions, String name, NameServer nameserver) {
        DHashTable actor = new DHashTable(name, nameserver);
        State state = new State(name, nameserver, actor.getStageId());

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null);
        Message input_message = new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation);
        MessageDirector md = MessageDirector.construct(3, new Object[]{input_message, arguments, token_positions});
        return output_continuation;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, int[] token_positions, String name, NameServer nameserver, int target_stage_id) {
        DHashTable actor = new DHashTable(name, nameserver, target_stage_id);
        State state = new State(name, nameserver, target_stage_id);

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null, target_stage_id);
        Message input_message = new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation);
        MessageDirector md = MessageDirector.construct(3, new Object[]{input_message, arguments, token_positions}, target_stage_id);
        return output_continuation;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, String name, NameServer nameserver, String host, int port) {
        DHashTable actor = new DHashTable(name, nameserver, host, port);
        State state = new State(name, nameserver, host, port, actor.getStageId());

        if (! (host.equals(TransportService.getHost()) && port == TransportService.getPort()) ) {
            synchronized (MobileActorRegistry.getStateLock(actor.hashCode())) {
                MobileActorRegistry.updateStateEntry(actor.hashCode(), TransportService.getSocket(host, port));
            }
            TransportService.migrateActor(host, port, state);
        }

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null);
        StageService.sendMessage(new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation));
        return output_continuation;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, String name, NameServer nameserver, String host, int port, int target_stage_id) {
        DHashTable actor = new DHashTable(name, nameserver, host, port, target_stage_id);
        State state = new State(name, nameserver, host, port, target_stage_id);

        if (! (host.equals(TransportService.getHost()) && port == TransportService.getPort()) ) {
            synchronized (MobileActorRegistry.getStateLock(actor.hashCode())) {
                MobileActorRegistry.updateStateEntry(actor.hashCode(), TransportService.getSocket(host, port));
            }
            TransportService.migrateActor(host, port, state);
        }

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null);
        StageService.sendMessage(new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation));
        return output_continuation;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, int[] token_positions, String name, NameServer nameserver, String host, int port) {
        DHashTable actor = new DHashTable(name, nameserver, host, port);
        State state = new State(name, nameserver, host, port, actor.getStageId());

        if (! (host.equals(TransportService.getHost()) && port == TransportService.getPort()) ) {
            synchronized (MobileActorRegistry.getStateLock(actor.hashCode())) {
                MobileActorRegistry.updateStateEntry(actor.hashCode(), TransportService.getSocket(host, port));
            }
            TransportService.migrateActor(host, port, state);
        }

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null);
        Message input_message = new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation);
        MessageDirector md = MessageDirector.construct(3, new Object[]{input_message, arguments, token_positions});
        return output_continuation;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, int[] token_positions, String name, NameServer nameserver, String host, int port, int target_stage_id) {
        DHashTable actor = new DHashTable(name, nameserver, host, port, target_stage_id);
        State state = new State(name, nameserver, host, port, target_stage_id);

        if (! (host.equals(TransportService.getHost()) && port == TransportService.getPort()) ) {
            synchronized (MobileActorRegistry.getStateLock(actor.hashCode())) {
                MobileActorRegistry.updateStateEntry(actor.hashCode(), TransportService.getSocket(host, port));
            }
            TransportService.migrateActor(host, port, state);
        }

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null, target_stage_id);
        Message input_message = new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation);
        MessageDirector md = MessageDirector.construct(3, new Object[]{input_message, arguments, token_positions}, target_stage_id);
        return output_continuation;
    }


    public static class State extends MobileActor.State {
        public State(String name, NameServer nameserver) { super(name, nameserver); }
        public State(String name, NameServer nameserver, int stage_id) { super(name, nameserver, stage_id); }

        public State(String name, NameServer nameserver, String host, int port) { super(name, nameserver, host, port); }

        public State(String name, NameServer nameserver, String host, int port, int stage_id) { super(name, nameserver, host, port, stage_id); }

        public void migrate(String host, int port) {
            if (! (host.equals(TransportService.getHost()) && port == TransportService.getPort()) ) {
                synchronized (MobileActorRegistry.getStateLock(this.hashCode())) {
                    MobileActorRegistry.updateStateEntry(this.hashCode(), TransportService.getSocket(host, port));
                }
                TransportService.migrateActor(host, port, this);
            }
        }

        StandardOutput origin_output;
        int num_of_nodes;
        long start;
        Chord chord;
        NameServer ns;
        ChordNode precedingNode;
        Hashtable<String,Integer> countItems = new Hashtable<String,Integer>(  );


        public Object invokeMessage(int messageId, Object[] arguments) throws RemoteMessageException, TokenPassException, MessageHandlerNotFoundException {
            switch(messageId) {
                case 0: return getPort();
                case 1: return getHost();
                case 2: return getName();
                case 3: return hashCode();
                case 4: return getLastKnownPort();
                case 5: return getLastKnownHost();
                case 6: migrate( (String)arguments[0], (Integer)arguments[1] ); return null;
                case 7: return getNameServer();
                case 8: go(); return null;
                case 9: put( (String)arguments[0], (String)arguments[1] ); return null;
                case 10: lookUp( (String)arguments[0] ); return null;
                case 11: return findNode( (String)arguments[0], (ChordKey)arguments[1], (ChordNode)arguments[2] );
                case 12: storedIn( (String)arguments[0], (ChordKey)arguments[1], (ChordNode)arguments[2] ); return null;
                case 13: process( (NameServer)arguments[0], (ArrayList<java.lang.String>)arguments[1], (ArrayList<java.lang.Integer>)arguments[2] ); return null;
                case 14: sortNodes( (Chord)arguments[0] ); return null;
                case 15: stabilizeNodes(); return null;
                case 16: setPreceding( (ChordNode)arguments[0] ); return null;
                case 17: stabilizeNode( (ChordNode)arguments[0] ); return null;
                case 18: fixFingerTable( (Chord)arguments[0] ); return null;
                case 19: printFingers( (Chord)arguments[0] ); return null;
                case 20: printChord( (Chord)arguments[0] ); return null;
                case 21: printTime(); return null;
                case 22: nodeStabilizer( (ChordNode)arguments[0], (ChordNode)arguments[1] ); return null;
                case 23: printNode( (String)arguments[0], (ChordNode)arguments[1] ); return null;
                case 24: printMessage( (String)arguments[0], (ChordNode)arguments[1] ); return null;
                case 25: test1(); return null;
                case 26: test2(); return null;
                case 27: addNode( (String)arguments[0], (Integer)arguments[1], (Chord)arguments[2] ); return null;
                case 28: removeNode(); return null;
                default: throw new MessageHandlerNotFoundException(messageId, arguments);
            }
        }

        public void invokeConstructor(int messageId, Object[] arguments) throws RemoteMessageException, ConstructorNotFoundException {
            switch(messageId) {
                case 0: construct( (StandardOutput)arguments[0] ); return;
                case 1: construct( (ArrayList<java.lang.String>)arguments[0], (ArrayList<java.lang.Integer>)arguments[1] ); return;
                case 2: construct( (String[])arguments[0] ); return;
                default: throw new ConstructorNotFoundException(messageId, arguments);
            }
        }

        public void construct(StandardOutput origin_output) {
            origin_output = origin_output;
        }

        public void construct(ArrayList<String> hosts, ArrayList<Integer> ports) {
            origin_output = StandardOutput.construct(0, null);
            ns = ((src.DHashTable.State)this).getNameServer();
            StageService.sendMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 13 /*process*/, new Object[]{ns, (ArrayList)DeepCopy.deepCopy( hosts ), (ArrayList)DeepCopy.deepCopy( ports )});
        }

        public void construct(String[] args) {
            start = System.currentTimeMillis();
            ns = ((src.DHashTable.State)this).getNameServer();
            ArrayList<String> hosts = new ArrayList<String>(  );
            ArrayList<Integer> ports = new ArrayList<Integer>(  );
            if (args.length != 3) {
                System.err.println("Run with the parameters <num of nodes> <host> <port>");
                System.exit(0);
            }
            
            num_of_nodes = Integer.parseInt(args[0]);
            for (int i = 0; i < num_of_nodes; i++) {
                hosts.add(args[1]);
                ports.add(Integer.parseInt(args[2]) + i);
            }

            StageService.sendMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 13 /*process*/, new Object[]{ns, (ArrayList)DeepCopy.deepCopy( hosts ), (ArrayList)DeepCopy.deepCopy( ports )});
        }



        public void go() {
            ContinuationDirector continuation_token = StageService.sendContinuationMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"Hello from: " + ((src.DHashTable.State)this).getName() + "!"});
            StageService.sendMessage(origin_output, 12 /*println*/, new Object[]{"Remote hello from: " + ((src.DHashTable.State)this).getName() + "!"}, continuation_token);
        }

        public void put(String key, String value) {
            ChordKey dataKey = new ChordKey( key );
            if (chord != null) {
                TokenDirector node = StageService.sendTokenMessage(chord, 6 /*getNode*/, new Object[]{0});
                int nodeNumber = Integer.parseInt(dataKey.toString()) % num_of_nodes;
                TokenDirector responsibleNode = StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{nodeNumber});
                StageService.sendMessage(null, 10 /*put*/, new Object[]{(ChordKey)DeepCopy.deepCopy( dataKey ), value}, responsibleNode);
            }
            
        }

        public void lookUp(String key) {
            ChordKey dataKey = new ChordKey( key );
            System.out.println("Search for key is " + key + " - " + dataKey);
            StageService.sendMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"Searching"});
            if (chord != null) {
                TokenDirector node = StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{0});
                StageService.sendMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 11 /*findNode*/, new Object[]{"Found in ", (ChordKey)DeepCopy.deepCopy( dataKey ), node}, new int[]{2});
            }
            
        }

        public ChordNode findNode(String message, ChordKey dataKey, ChordNode node) throws TokenPassException {
            if (node != null) {
                TokenDirector responsibleNode = StageService.sendTokenMessage(node, 20 /*findSuccessor*/, new Object[]{(ChordKey)DeepCopy.deepCopy( dataKey )});
                StageService.passToken(responsibleNode, this.getStage().message.continuationDirector);
                throw new TokenPassException();
            }
            else {
                StageService.sendMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{node + " >>>  Null node is rejected <<<"});
            }

            return null;
        }

        public void storedIn(String message, ChordKey key, ChordNode responsibleNode) {
            StageService.sendMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{message + " Key " + key + " : Responsible node :" + responsibleNode});
        }

        public void process(NameServer ns, ArrayList<String> hosts, ArrayList<Integer> ports) throws TokenPassException {
            System.out.println("NameServer = " + ns);
            chord = Chord.construct(0, null);
            JoinDirector jd = JoinDirector.construct(0, null);
            num_of_nodes = hosts.size();
            TokenDirector rc;
            System.out.println("host :" + hosts.size() + " num : " + num_of_nodes);
            for (int i = 0; i < num_of_nodes; i++) {
                ContinuationDirector continuation_token = StageService.sendContinuationMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"finished go() at remote theater then migrate and go back to local theater."});
                continuation_token = StageService.sendContinuationMessage(chord, 4 /*getListSize*/, null, continuation_token);
                continuation_token = StageService.sendContinuationMessage(chord, 2 /*createNode*/, new Object[]{ChordNode.construct(0, new Object[]{StandardOutput.construct(0, null)}, "rc_" + i, ns, hosts.get(i), ports.get(i))}, new int[]{0}, continuation_token);
                continuation_token = StageService.sendContinuationMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"Creating node " + (i + 1)}, continuation_token);
                StageService.sendMessage(jd, 2 /*join*/, null, continuation_token);
            }

            ContinuationDirector continuation_token = StageService.sendContinuationMessage(jd, 3 /*resolveAfter*/, new Object[]{num_of_nodes});
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 14 /*sortNodes*/, new Object[]{chord}, continuation_token);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 15 /*stabilizeNodes*/, null, continuation_token);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 20 /*printChord*/, new Object[]{chord}, continuation_token);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 18 /*fixFingerTable*/, new Object[]{chord}, continuation_token);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 19 /*printFingers*/, new Object[]{chord}, continuation_token);
            continuation_token = StageService.sendContinuationMessage(origin_output, 12 /*println*/, new Object[]{"DONE"}, continuation_token);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 26 /*test2*/, null, continuation_token);
            StageService.sendPassMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 28 /*removeNode*/, null, continuation_token, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }

        public void sortNodes(Chord chord) throws TokenPassException {
            JoinDirector jd = JoinDirector.construct(0, null);
            for (int i = 0; i < num_of_nodes; i++) {
                ContinuationDirector continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{i + " From sort Node : ", StageService.sendImplicitTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{i})}, new int[]{1});
                StageService.sendMessage(jd, 2 /*join*/, null, continuation_token);
                System.out.println("Sort = " + i);
            }

            ContinuationDirector continuation_token = StageService.sendContinuationMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"Nodes are sorted"});
            StageService.sendPassMessage(jd, 3 /*resolveAfter*/, new Object[]{num_of_nodes}, continuation_token, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }

        public void stabilizeNodes() throws TokenPassException {
            JoinDirector jd = JoinDirector.construct(0, null);
            StageService.sendMessage(origin_output, 12 /*println*/, new Object[]{"From Stabilize Node : "});
            TokenDirector thisNode = StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{0});
            TokenDirector rootNode = StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{0});
            TokenDirector precedingNode = TokenDirector.construct(1, new Object[]{null});
            TokenDirector node;
            ContinuationDirector continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 23 /*printNode*/, new Object[]{" Root Node is ", thisNode}, new int[]{1});
            for (int i = 0; i < num_of_nodes - 1; i++) {
                node = StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{i + 1});
                continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 23 /*printNode*/, new Object[]{"From stabilize node: ", node}, new int[]{1}, continuation_token);
                continuation_token = StageService.sendContinuationMessage(null, 37 /*setSuccessor*/, new Object[]{node}, new int[]{0}, continuation_token, thisNode);
                continuation_token = StageService.sendContinuationMessage(null, 35 /*setPredecessor*/, new Object[]{precedingNode}, new int[]{0}, continuation_token, thisNode);
                precedingNode = thisNode;
                thisNode = node;
                continuation_token = StageService.sendContinuationMessage(null, 11 /*stabilize*/, null, continuation_token, node);
                continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 22 /*nodeStabilizer*/, new Object[]{precedingNode, StageService.sendImplicitTokenMessage(null, 36 /*getSuccessor*/, null, continuation_token, node)}, new int[]{0, 1});
                StageService.sendMessage(jd, 2 /*join*/, null, continuation_token);
            }

            continuation_token = StageService.sendContinuationMessage(jd, 3 /*resolveAfter*/, new Object[]{num_of_nodes - 2});
            continuation_token = StageService.sendContinuationMessage(null, 37 /*setSuccessor*/, new Object[]{rootNode}, new int[]{0}, continuation_token, thisNode);
            continuation_token = StageService.sendContinuationMessage(null, 35 /*setPredecessor*/, new Object[]{precedingNode}, new int[]{0}, continuation_token, thisNode);
            continuation_token = StageService.sendContinuationMessage(null, 35 /*setPredecessor*/, new Object[]{thisNode}, new int[]{0}, continuation_token, rootNode);
            StageService.sendPassMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"Chord ring is established."}, continuation_token, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }

        public void setPreceding(ChordNode node) {
            precedingNode = node;
        }

        public void stabilizeNode(ChordNode node) throws TokenPassException {
            StageService.sendPassMessage(node, 11 /*stabilize*/, null, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }

        public void fixFingerTable(Chord chord) throws TokenPassException {
            JoinDirector jd = JoinDirector.construct(0, null);
            for (int i = 0; i < num_of_nodes; i++) {
                if (chord != null) {
                    ContinuationDirector continuation_token = StageService.sendContinuationMessage(null, 16 /*fixFingers*/, null, StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{i}));
                    StageService.sendMessage(jd, 2 /*join*/, null, continuation_token);
                }
                else {
                    System.out.println("Chord is null");
                }
            }

            ContinuationDirector continuation_token = StageService.sendContinuationMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"Finger Tables are fixed."});
            StageService.sendPassMessage(jd, 3 /*resolveAfter*/, new Object[]{num_of_nodes}, continuation_token, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }

        public void printFingers(Chord chord) throws TokenPassException {
            JoinDirector jd = JoinDirector.construct(0, null);
            for (int i = 0; i < num_of_nodes; i++) {
                ContinuationDirector continuation_token = StageService.sendContinuationMessage(null, 27 /*printFingerTable*/, null, StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{i}));
                StageService.sendMessage(jd, 2 /*join*/, null, continuation_token);
            }

            StageService.sendPassMessage(jd, 3 /*resolveAfter*/, new Object[]{num_of_nodes}, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }

        public void printChord(Chord chord) throws TokenPassException {
            ContinuationDirector continuation_token = StageService.sendContinuationMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"Print Chord *****"});
            StageService.sendMessage(chord, 3 /*getSortedNodeSize*/, null, continuation_token);
            JoinDirector jd = JoinDirector.construct(0, null);
            TokenDirector node;
            System.out.println("Num of nodes " + num_of_nodes);
            for (int i = 0; i < num_of_nodes; i++) {
                continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{"Printing Chord: ", StageService.sendImplicitTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{i})}, new int[]{1});
                node = StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{i}, continuation_token);
                continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{i + " : Successor: ", StageService.sendImplicitTokenMessage(null, 36 /*getSuccessor*/, null, continuation_token, node)}, new int[]{1});
                continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{i + " : Pedecessor: ", StageService.sendImplicitTokenMessage(null, 34 /*getPredecessor*/, null, continuation_token, node)}, new int[]{1});
                StageService.sendMessage(jd, 2 /*join*/, null, continuation_token);
            }

            continuation_token = StageService.sendContinuationMessage(jd, 3 /*resolveAfter*/, new Object[]{num_of_nodes});
            StageService.sendPassMessage(origin_output, 12 /*println*/, new Object[]{"Printed"}, continuation_token, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }

        public void printTime() {
            long end = System.currentTimeMillis();
            int interval = (int)(end - start);
            System.err.println("Elapsed Time :" + interval / 1000 + interval % 1000);
        }

        public void nodeStabilizer(ChordNode predecessor, ChordNode successor) {
            if (predecessor == null) {
                StageService.sendMessage(successor, 11 /*stabilize*/, null);
            }
            else {
                StageService.sendMessage(predecessor, 11 /*stabilize*/, null);
            }

        }

        public void printNode(String message, ChordNode node) {
        }

        public void printMessage(String message, ChordNode node) throws TokenPassException {
            ContinuationDirector continuation_token = StageService.sendContinuationMessage(origin_output, 12 /*println*/, new Object[]{"---------------------------------------"});
            StageService.sendPassMessage(origin_output, 12 /*println*/, new Object[]{message + " " + node}, continuation_token, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }

        public void test1() {
            Scanner sc2 = null;
            try {
                sc2 = new Scanner( new File( "dict.txt" ) );
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            while (sc2.hasNextLine()) {
                String line = sc2.nextLine();
                String[] arr = line.split("\t");
                String firstWord = arr[0];
                String theRest = arr[1];
                StageService.sendMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 9 /*put*/, new Object[]{firstWord, theRest});
            }

        }

        public void test2() {
            int i = 0;
            Scanner sc2 = null;
            try {
                sc2 = new Scanner( new File( "wordlist.rtf" ) );
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            while (sc2.hasNextLine()) {
                String line = sc2.nextLine();
                i++;
                StageService.sendMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 9 /*put*/, new Object[]{line, line});
            }

            System.out.println("i = " + i);
        }

        public void addNode(String host, int port, Chord chord) {
            num_of_nodes += 1;
            TokenDirector rootNode = StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{0});
            TokenDirector node = ChordNode.construct(0, new Object[]{StandardOutput.construct(0, null)}, "rc_" + num_of_nodes, ns, host, port);
            ContinuationDirector continuation_token = StageService.sendContinuationMessage(chord, 2 /*createNode*/, new Object[]{node}, new int[]{0});
            class ExpressionDirector1 extends Actor {
                public ExpressionDirector1(int stage_id) { super(stage_id); }
                public void invokeConstructor(int id, Object[] arguments) {}
                public Object invokeMessage(int messageId, Object[] arguments) {
                    return "Node Adding : " + (ChordNode)arguments[0];
                }
            }
            continuation_token = StageService.sendContinuationMessage(origin_output, 12 /*println*/, new Object[]{StageService.sendImplicitTokenMessage(new ExpressionDirector1(this.getStageId()), 0, new Object[]{node}, new int[]{0})}, new int[]{0}, continuation_token);
            TokenDirector nearestNode = StageService.sendTokenMessage(null, 20 /*findSuccessor*/, new Object[]{StageService.sendImplicitTokenMessage(null, 32 /*getNodeKey*/, null, continuation_token, node)}, new int[]{0}, rootNode);
            StageService.sendMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{"New node ", node}, new int[]{1});
            StageService.sendPassMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{"Nearest node ", nearestNode}, new int[]{1}, this.getStage().message.continuationDirector);
            return;
        }

        public void removeNode() throws TokenPassException {
            TokenDirector node = StageService.sendTokenMessage(chord, 7 /*getSortedNode*/, new Object[]{2});
            TokenDirector prede = StageService.sendTokenMessage(null, 34 /*getPredecessor*/, null, node);
            TokenDirector succe = StageService.sendTokenMessage(null, 36 /*getSuccessor*/, null, node);
            ContinuationDirector continuation_token = StageService.sendContinuationMessage(origin_output, 12 /*println*/, new Object[]{"Removing Node"});
            continuation_token = StageService.sendContinuationMessage(null, 37 /*setSuccessor*/, new Object[]{node}, new int[]{0}, continuation_token, node);
            continuation_token = StageService.sendContinuationMessage(null, 35 /*setPredecessor*/, new Object[]{node}, new int[]{0}, continuation_token, node);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{"Successor is ", succe}, new int[]{1}, continuation_token);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{"Predecessor is ", prede}, new int[]{1}, continuation_token);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{"Successor null ", StageService.sendImplicitTokenMessage(null, 36 /*getSuccessor*/, null, continuation_token, node)}, new int[]{1});
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 24 /*printMessage*/, new Object[]{"Predecessor null ", StageService.sendImplicitTokenMessage(null, 34 /*getPredecessor*/, null, continuation_token, node)}, new int[]{1});
            continuation_token = StageService.sendContinuationMessage(null, 37 /*setSuccessor*/, new Object[]{succe}, new int[]{0}, continuation_token, prede);
            continuation_token = StageService.sendContinuationMessage(null, 35 /*setPredecessor*/, new Object[]{prede}, new int[]{0}, continuation_token, succe);
            continuation_token = StageService.sendContinuationMessage(origin_output, 12 /*println*/, new Object[]{"Printing Chord after Removing 2"}, continuation_token);
            continuation_token = StageService.sendContinuationMessage(((src.DHashTable)((src.DHashTable.State)this).getStage().message.target), 20 /*printChord*/, new Object[]{chord}, continuation_token);
            num_of_nodes -= 1;
            StageService.sendMessage(chord, 8 /*removeNode*/, new Object[]{StageService.sendImplicitTokenMessage(null, 32 /*getNodeKey*/, null, node)}, new int[]{0});
            StageService.sendPassMessage(origin_output, 12 /*println*/, new Object[]{"Printing Chord DONE after Removing 2"}, this.getStage().message.continuationDirector);
            throw new TokenPassException();
        }


    }
}
