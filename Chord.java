package src;

/****** SALSA LANGUAGE IMPORTS ******/
import salsa_lite.common.DeepCopy;
import salsa_lite.runtime.LocalActorRegistry;
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

/****** END SALSA LANGUAGE IMPORTS ******/

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javasrc.ChordException;
import javasrc.ChordKey;

public class Chord extends salsa_lite.runtime.Actor implements java.io.Serializable {


    public Object writeReplace() throws java.io.ObjectStreamException {
        int hashCode = Hashing.getHashCodeFor(this.hashCode(), TransportService.getHost(), TransportService.getPort());
        synchronized (LocalActorRegistry.getLock(hashCode)) {
            LocalActorRegistry.addEntry(hashCode, this);
        }
        return new SerializedChord( this.hashCode(), TransportService.getHost(), TransportService.getPort() );
    }

    public static class RemoteReference extends Chord {
        private int hashCode;
        private String host;
        private int port;
        RemoteReference(int hashCode, String host, int port) { this.hashCode = hashCode; this.host = host; this.port = port; }

        public Object invokeMessage(int messageId, Object[] arguments) throws RemoteMessageException, TokenPassException, MessageHandlerNotFoundException {
            TransportService.sendMessageToRemote(host, port, this.getStage().message);
            throw new RemoteMessageException();
        }

        public void invokeConstructor(int messageId, Object[] arguments) throws RemoteMessageException, ConstructorNotFoundException {
            TransportService.sendMessageToRemote(host, port, this.getStage().message);
            throw new RemoteMessageException();
        }

        public Object writeReplace() throws java.io.ObjectStreamException {
            return new SerializedChord( hashCode, host, port);
        }
    }

    public static class SerializedChord implements java.io.Serializable {
        int hashCode;
        String host;
        int port;

        SerializedChord(int hashCode, String host, int port) { this.hashCode = hashCode; this.host = host; this.port = port; }

        public Object readResolve() throws java.io.ObjectStreamException {
            int hashCode = Hashing.getHashCodeFor(this.hashCode, this.host, this.port);
            synchronized (LocalActorRegistry.getLock(hashCode)) {
                Chord actor = (Chord)LocalActorRegistry.getEntry(hashCode);
                if (actor == null) {
                    RemoteReference remoteReference = new RemoteReference(this.hashCode, this.host, this.port);
                    LocalActorRegistry.addEntry(hashCode, remoteReference);
                    return remoteReference;
                } else {
                    return actor;
                }
            }
        }
    }

    public Chord() { super(); }
    public Chord(int stage_id) { super(stage_id); }

    List<ChordNode> nodeList = new ArrayList<ChordNode>(  );
    SortedMap<ChordKey,ChordNode> sortedNodeMap = new TreeMap<ChordKey,ChordNode>(  );
    Object[] sortedKeyArray;


    public Object invokeMessage(int messageId, Object[] arguments) throws RemoteMessageException, TokenPassException, MessageHandlerNotFoundException {
        switch(messageId) {
            case 0: return toString();
            case 1: return hashCode();
            case 2: createNode( (ChordNode)arguments[0] ); return null;
            case 3: getSortedNodeSize(); return null;
            case 4: getListSize(); return null;
            case 5: createNode( (ChordKey)arguments[0], (ChordNode)arguments[1] ); return null;
            case 6: return getNode( (Integer)arguments[0] );
            case 7: return getSortedNode( (Integer)arguments[0] );
            case 8: removeNode( (ChordKey)arguments[0] ); return null;
            default: throw new MessageHandlerNotFoundException(messageId, arguments);
        }
    }

    public void invokeConstructor(int messageId, Object[] arguments) throws RemoteMessageException, ConstructorNotFoundException {
        switch(messageId) {
            case 0: construct(); return;
            default: throw new ConstructorNotFoundException(messageId, arguments);
        }
    }

    public void construct() {}



    public void createNode(ChordNode node) throws TokenPassException {
        nodeList.add(node);
        StageService.sendPassMessage(((src.Chord)this), 5 /*createNode*/, new Object[]{StageService.sendImplicitTokenMessage(node, 32 /*getNodeKey*/, null), node}, new int[]{0}, this.getStage().message.continuationDirector);
        throw new TokenPassException();
    }

    public void getSortedNodeSize() {
        System.out.println("Sorted Node size " + sortedNodeMap.size());
    }

    public void getListSize() {
        System.out.println("List size " + nodeList.size());
    }

    public void createNode(ChordKey nodeKey, ChordNode node) {
        if (sortedNodeMap.get(nodeKey) != null) {
            new ChordException( "Duplicated Key: " + node );
        }
        
        sortedNodeMap.put(nodeKey, node);
    }

    public ChordNode getNode(int i) {
        return (ChordNode)nodeList.get(i);
    }

    public ChordNode getSortedNode(int i) {
        System.out.println(" i = " + i);
        if (sortedKeyArray == null) {
            sortedKeyArray = sortedNodeMap.keySet().toArray();
        }
        
        return (ChordNode)sortedNodeMap.get(sortedKeyArray[i]);
    }

    public void removeNode(ChordKey nodeKey) {
        sortedNodeMap.remove(nodeKey);
        sortedKeyArray = sortedNodeMap.keySet().toArray();
    }


    public static Chord construct(int constructor_id, Object[] arguments) {
        Chord actor = new Chord();
        StageService.sendMessage(new Message(Message.CONSTRUCT_MESSAGE, actor, constructor_id, arguments));
        return actor;
    }

    public static Chord construct(int constructor_id, Object[] arguments, int target_stage_id) {
        Chord actor = new Chord(target_stage_id);
        actor.getStage().putMessageInMailbox(new Message(Message.CONSTRUCT_MESSAGE, actor, constructor_id, arguments));
        return actor;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, int[] token_positions) {
        Chord actor = new Chord();
        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null);
        Message input_message = new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation);
        MessageDirector md = MessageDirector.construct(3, new Object[]{input_message, arguments, token_positions});
        return output_continuation;
    }

    public static TokenDirector construct(int constructor_id, Object[] arguments, int[] token_positions, int target_stage_id) {
        Chord actor = new Chord(target_stage_id);
        TokenDirector output_continuation = TokenDirector.construct(0 /*construct()*/, null, target_stage_id);
        Message input_message = new Message(Message.CONSTRUCT_CONTINUATION_MESSAGE, actor, constructor_id, arguments, output_continuation);
        MessageDirector md = MessageDirector.construct(3, new Object[]{input_message, arguments, token_positions}, target_stage_id);
        return output_continuation;
    }

}
