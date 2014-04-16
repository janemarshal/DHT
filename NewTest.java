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
import java.io.PrintStream;
import java.util.ArrayList;
import salsa_lite.runtime.wwc.NameServer;

public class NewTest extends MobileActor implements java.io.Serializable {

    public Object writeReplace() throws java.io.ObjectStreamException {
        return new SerializedNewTest( this.getName(), this.getNameServer(), this.getLastKnownHost(), this.getLastKnownPort());
    }

    public static class SerializedNewTest implements java.io.Serializable {
        String name;
        String lastKnownHost;
        int lastKnownPort;

        NameServer nameserver;

        SerializedNewTest(String name, NameServer nameserver, String lastKnownHost, int lastKnownPort) { this.name = name; this.nameserver = nameserver; this.lastKnownHost = lastKnownHost; this.lastKnownPort = lastKnownPort; }

        public Object readResolve() throws java.io.ObjectStreamException {
            int hashCode = Hashing.getHashCodeFor(name, nameserver.getName(), nameserver.getHost(), nameserver.getPort());

                synchronized (MobileActorRegistry.getStateLock(hashCode)) {
                    Actor entry = MobileActorRegistry.getStateEntry(hashCode);
                    if (entry == null) {
                        MobileActorRegistry.addStateEntry(hashCode, TransportService.getSocket(lastKnownHost, lastKnownPort));
                    }
                }

            synchronized (MobileActorRegistry.getReferenceLock(hashCode)) {
                NewTest actor = (NewTest)MobileActorRegistry.getReferenceEntry(hashCode);
                if (actor == null) {
                    NewTest remoteReference = new NewTest(name, nameserver, lastKnownHost, lastKnownPort);
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


    public NewTest(String name, NameServer nameserver) { super(name, nameserver); }
    public NewTest(String name, NameServer nameserver, int stage_id) { super(name, nameserver, stage_id); }
    public NewTest(String name, NameServer nameserver, String lastKnownHost, int lastKnownPort) { super(name, nameserver, lastKnownHost, lastKnownPort); }

    public NewTest(String name, NameServer nameserver, String lastKnownHost, int lastKnownPort, int stage_id) { super(name, nameserver, lastKnownHost, lastKnownPort, stage_id); }

    public static void main(String[] arguments) {
        TransportService.initialize();
        String name = System.getProperty("called");
        String nameserver_info = System.getProperty("using");
        if (name == null || nameserver_info == null) {
            System.err.println("Error starting NewTest: to run a mobile actor you must specify a name with the '-Dcalled=<name>' system property and a namesever with the '-Dusing=\"<nameserver_host>:<nameserver_port>/<nameserver_name>\"' system property.");
            System.err.println("usage: (port is optional and 4040 by default)");
            System.err.println("	java -Dcalled=\"<name>\" [-Dport=4040] src.NewTest");
            System.exit(0);
        }
        try {
            int colon_index = nameserver_info.indexOf(':');
            int slash_index = nameserver_info.indexOf('/');
            String nameserver_host = nameserver_info.substring(0,colon_index);
            int nameserver_port = Integer.parseInt(nameserver_info.substring(colon_index + 1, slash_index));
            String nameserver_name = nameserver_info.substring(slash_index + 1, nameserver_info.length());
            NewTest.construct(0, new Object[]{arguments}, name, NameServer.getRemoteReference(nameserver_name, nameserver_host, nameserver_port));
        } catch (Exception e) {
            System.err.println("Error in format of -Dusing system property, needs to be 'nameserver_host:nameserver_port/nameserver_name'.");
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static NewTest construct(int constructor_id, Object[] arguments, String name, NameServer nameserver) {
        NewTest actor = new NewTest(name, nameserver);
        State state = new State(name, nameserver, actor.getStageId());

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        StageService.sendMessage(new Message(Message.CONSTRUCT_MESSAGE, actor, constructor_id, arguments));
        return actor;
    }

    public static NewTest construct(int constructor_id, Object[] arguments, String name, NameServer nameserver, int target_stage_id) {
        NewTest actor = new NewTest(name, nameserver, target_stage_id);
        State state = new State(name, nameserver, target_stage_id);

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        actor.getStage().putMessageInMailbox(new Message(Message.CONSTRUCT_MESSAGE, actor, constructor_id, arguments));
        return actor;
    }


    public static TokenDirector construct(int constructor_id, Object[] arguments, int[] token_positions, String name, NameServer nameserver) {
        NewTest actor = new NewTest(name, nameserver);
        State state = new State(name, nameserver, actor.getStageId());

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null);
        Message input_message = new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation);
        MessageDirector md = MessageDirector.construct(3, new Object[]{input_message, arguments, token_positions});
        return output_continuation;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, int[] token_positions, String name, NameServer nameserver, int target_stage_id) {
        NewTest actor = new NewTest(name, nameserver, target_stage_id);
        State state = new State(name, nameserver, target_stage_id);

        StageService.sendMessage(new Message(Message.SIMPLE_MESSAGE, nameserver, 4 /*put*/, new Object[]{actor})); //register the actor with the name server. 

        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null, target_stage_id);
        Message input_message = new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation);
        MessageDirector md = MessageDirector.construct(3, new Object[]{input_message, arguments, token_positions}, target_stage_id);
        return output_continuation;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, String name, NameServer nameserver, String host, int port) {
        NewTest actor = new NewTest(name, nameserver, host, port);
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
        NewTest actor = new NewTest(name, nameserver, host, port, target_stage_id);
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
        NewTest actor = new NewTest(name, nameserver, host, port);
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
        NewTest actor = new NewTest(name, nameserver, host, port, target_stage_id);
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
                default: throw new MessageHandlerNotFoundException(messageId, arguments);
            }
        }

        public void invokeConstructor(int messageId, Object[] arguments) throws RemoteMessageException, ConstructorNotFoundException {
            switch(messageId) {
                case 0: construct( (String[])arguments[0] ); return;
                default: throw new ConstructorNotFoundException(messageId, arguments);
            }
        }

        public void construct() {}

        public void construct(String[] ar) {
            ArrayList<String> hosts = new ArrayList<String>(  );
            ArrayList<Integer> ports = new ArrayList<Integer>(  );
            NameServer ns = ((src.NewTest.State)this).getNameServer();
            hosts.add("127.0.0.1");
            ports.add(4040);
            hosts.add("127.0.0.1");
            ports.add(4041);
            hosts.add("127.0.0.1");
            ports.add(4042);
            hosts.add("127.0.0.1");
            ports.add(4043);
            hosts.add("127.0.0.1");
            ports.add(4044);
            StandardOutput output = StandardOutput.construct(0, null);
            JoinDirector jd = JoinDirector.construct(0, null);
            ContinuationDirector continuation_token = StageService.sendContinuationMessage(StandardOutput.construct(0, null), 12 /*println*/, new Object[]{"Start"});
            DHashTable dht = DHashTable.construct(1, new Object[]{hosts, ports}, "RemoteHash", ns);
        }




    }
}
