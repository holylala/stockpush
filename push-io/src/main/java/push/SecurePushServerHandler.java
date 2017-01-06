package push;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import push.message.Entity;

import java.net.InetAddress;

public class SecurePushServerHandler extends SimpleChannelInboundHandler<Entity.BaseEntity> {
    private Logger logger= LoggerFactory.getLogger(SecurePushServerHandler.class);
    private  final EventManager<ConnectionEvent> eventManager;
    public SecurePushServerHandler(EventManager eventManager){
        this.eventManager=eventManager;
    }
//    @Override
//    public void channelInactive(ChannelHandlerContext ctx) throws Exception{
//        ctx.channel().close();
//    }
    //static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        // Once session is secured, send a greeting and register the channel to the global channel
        // list so the channel received the messages from others.
        ctx.pipeline().get(SslHandler.class).handshakeFuture().addListener(
                new GenericFutureListener<Future<Channel>>() {
                    @Override
                    public void operationComplete(Future<Channel> future) throws Exception {
                        String msg="Welcome to " + InetAddress.getLocalHost().getHostName() + " secure chat service!\n";
                        msg+="Your session is protected by " +
                                        ctx.pipeline().get(SslHandler.class).engine().getSession().getCipherSuite() +
                                        " cipher suite.\n";
                        Entity.Message.Builder msgBuilder=Entity.Message.newBuilder();
                        msgBuilder.setMessage(msg);
                        msgBuilder.setFrom(0);
                        msgBuilder.setTo(0);
                        Entity.BaseEntity.Builder builder=Entity.BaseEntity.newBuilder();
                        builder.setType(Entity.Type.MESSAGE);
                        builder.setExtension(Entity.message,msgBuilder.build());
                        ctx.writeAndFlush(builder.build()).sync();
                    }
        });
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Entity.BaseEntity msg) throws Exception {
        if(msg.getType()==Entity.Type.PING){
            //这段代码只是为了提前结束判断
        }
        else if(msg.getType()==Entity.Type.LOGIN){
            Entity.Login login=msg.getExtension(Entity.login);
            ConnectionEvent ce=new ConnectionEvent();
            ce.setCet(ConnectionEvent.ConnectionEventType.CONNECTION_ADD);
            ce.setChc(ctx);
            ce.setUid(login.getUid());
            eventManager.add(ce);
            //sps.channels.put(login.getUid(),ctx);
        }else if(msg.getType()==Entity.Type.LOGOUT){
            Entity.Logout logout=msg.getExtension(Entity.logout);
            ConnectionEvent ce=new ConnectionEvent();
            ce.setCet(ConnectionEvent.ConnectionEventType.CONNECTION_REMOVE);
            ce.setChc(ctx);
            ce.setUid(logout.getUid());
            eventManager.add(ce);
            //sps.channels.remove(logout.getUid());
            //ctx.close();
        }else if(msg.getType()==Entity.Type.MESSAGE){
            Entity.Message message=msg.getExtension(Entity.message);
//            long to=message.getTo();
//            ChannelHandlerContext toContext=sps.channels.get(to);
//            if(toContext!=null)
//                toContext.writeAndFlush(msg);
            ConnectionEvent ce=new ConnectionEvent();
            ce.setCet(ConnectionEvent.ConnectionEventType.MESSAGE_TRANSFER);
            ce.setMessage(msg);
            eventManager.add(ce);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//        ctx.close();
//        ctx.channel().close();
        ConnectionEvent ce=new ConnectionEvent();
        ce.setCet(ConnectionEvent.ConnectionEventType.CONNECTION_REMOVE);
        ce.setChc(ctx);
        eventManager.add(ce);
        logger.error("remote client error",cause);
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception{
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state().equals(IdleState.READER_IDLE)) {
                logger.error("READER_IDLE");
                // 超时关闭channel
                ctx.close();
            } else if (event.state().equals(IdleState.WRITER_IDLE)) {
                logger.error("WRITER_IDLE");
            } else if (event.state().equals(IdleState.ALL_IDLE)) {
                logger.error("ALL_IDLE");
                // 发送心跳
                //这段代码可以提出去啊
                Entity.Ping.Builder ping=Entity.Ping.newBuilder();
                ping.setMessage("ping");
                Entity.BaseEntity.Builder base=Entity.BaseEntity.newBuilder();
                base.setType(Entity.Type.PING);
                base.setExtension(Entity.ping,ping.build());
                //ctx.channel().write(base.build());
                ctx.writeAndFlush(base.build());
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
