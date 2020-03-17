package com.alibaba.ttl.threadpool.agent.internal.transformlet.impl;

import com.alibaba.ttl.threadpool.agent.internal.logging.Logger;
import com.alibaba.ttl.threadpool.agent.internal.transformlet.ClassInfo;
import com.alibaba.ttl.threadpool.agent.internal.transformlet.JavassistTransformlet;
import edu.umd.cs.findbugs.annotations.NonNull;
import javassist.*;

import java.io.IOException;
import java.lang.reflect.Modifier;

import static com.alibaba.ttl.threadpool.agent.internal.transformlet.impl.Utils.renamedMethodNameByTtl;
import static com.alibaba.ttl.threadpool.agent.internal.transformlet.impl.Utils.signatureOfMethod;

public class TtlMailboxTransformlet implements JavassistTransformlet {
    private static final Logger logger = Logger.getLogger(TtlMailboxTransformlet.class);

    private static final String CAPTURED_FIELD_NAME = "captured$field$added$by$ttl";
    private static final String CURRENT_CAPTURED_FIELD_VALUE = "captured2$field$added$by$ttl";
    private static final String ACTOR_CELL_NAME = "akka.dispatch.Mailbox";


    public TtlMailboxTransformlet() {
    }

    @Override
    public void doTransform(@NonNull final ClassInfo classInfo) throws IOException, NotFoundException, CannotCompileException {
        if (ACTOR_CELL_NAME.equals(classInfo.getClassName())) {
            updateMailbox(classInfo.getCtClass());
            classInfo.setModified();
        }
    }

    /**
     * @param clazz
     * @see akka.dispatch.Mailbox
     */
    private void updateMailbox(@NonNull final CtClass clazz) throws CannotCompileException, NotFoundException {
        final String className = clazz.getName();
        // add new field
        final CtField capturedField = CtField.make("public final java.util.concurrent.ConcurrentHashMap " + CAPTURED_FIELD_NAME + ";", clazz);
        clazz.addField(capturedField, "new java.util.concurrent.ConcurrentHashMap()");
        logger.info("add filed " + capturedField + " for clazz:" + className);

        final CtField capturedField2 = CtField.make("public Object " + CURRENT_CAPTURED_FIELD_VALUE + ";", clazz);
        clazz.addField(capturedField2, "null");
        logger.info("add filed " + capturedField2 + " for clazz:" + className);

        updateEnqueue(clazz);
        updateDequeue(clazz);
        updateProcessMailbox(clazz);
        updateCleanUp(clazz);

    }

    /**
     * @see akka.dispatch.Mailbox.enqueue
     */
    private void updateEnqueue(@NonNull CtClass clazz) throws NotFoundException, CannotCompileException {
        final CtMethod enqueueMethod = clazz.getDeclaredMethod("enqueue");
        final String enqueue_renamed_method_rename = renamedMethodNameByTtl(enqueueMethod);
        final String beforeCode = "Object capture = com.alibaba.ttl.threadpool.agent.internal.transformlet.impl.Utils.doCaptureWhenNotTtlEnhanced(this);\n" +
            "  " + CAPTURED_FIELD_NAME + ".put($2, capture); ";
        final String tryCode = "" + enqueue_renamed_method_rename + "($1, $2);\n";
        final String finallyCode = "";

        doTryFinallyForMethod(enqueueMethod, enqueue_renamed_method_rename, beforeCode, tryCode, finallyCode);
    }

    /**
     * @see akka.dispatch.Mailbox.dequeue
     */
    private void updateDequeue(@NonNull CtClass clazz) throws NotFoundException, CannotCompileException {
        final CtMethod dequeueMethod = clazz.getDeclaredMethod("dequeue");
        final String dequeue_renamed_method_rename = renamedMethodNameByTtl(dequeueMethod);
        final String beforeCode = "Object backup = null;\n";

        final String tryCode = "akka.dispatch.Envelope envelope = " + dequeue_renamed_method_rename + "($$);\n" +
            "if (envelope != null && " + CAPTURED_FIELD_NAME + ".get(envelope) != null) {\n" +
            "    backup = com.alibaba.ttl.TransmittableThreadLocal.Transmitter.replay(" + CAPTURED_FIELD_NAME + ".remove(envelope));\n" +
            "}\n" +
            "return envelope;";

        final String finallyCode = "if(backup != null){" +
            "    " + CURRENT_CAPTURED_FIELD_VALUE + " = backup;" +
            "}\n";

        doTryFinallyForMethod(dequeueMethod, dequeue_renamed_method_rename, beforeCode, tryCode, finallyCode);
    }

    /**
     * @see akka.dispatch.Mailbox.processMailbox
     */
    private void updateProcessMailbox(@NonNull CtClass clazz) throws NotFoundException, CannotCompileException {
        final CtMethod dequeueMethod = clazz.getDeclaredMethod("processMailbox");
        final String processMailBox_renamed_method_rename = renamedMethodNameByTtl(dequeueMethod);
        //int left, long deadlineNs
        final String beforeCode = "if (" + CURRENT_CAPTURED_FIELD_VALUE + " != null) {\n" +
            "  com.alibaba.ttl.TransmittableThreadLocal.Transmitter.restore(" + CURRENT_CAPTURED_FIELD_VALUE + ");\n" +
            "  " + CURRENT_CAPTURED_FIELD_VALUE + " = null;\n" +
            "}\n";

        final String tryCode = "  " + processMailBox_renamed_method_rename + "($1, $2);\n";

        final String finallyCode = "if (" + CURRENT_CAPTURED_FIELD_VALUE + " != null) {\n" +
            "  com.alibaba.ttl.TransmittableThreadLocal.Transmitter.restore(" + CURRENT_CAPTURED_FIELD_VALUE + ");\n" +
            "  " + CURRENT_CAPTURED_FIELD_VALUE + " = null;\n" +
            "}\n";

        doTryFinallyForMethod(dequeueMethod, processMailBox_renamed_method_rename, beforeCode, tryCode, finallyCode);
    }

    /**
     * @see akka.dispatch.Mailbox.cleanUp
     */
    private void updateCleanUp(@NonNull CtClass clazz) throws NotFoundException, CannotCompileException {
        final CtMethod dequeueMethod = clazz.getDeclaredMethod("cleanUp");
        final String processMailBox_renamed_method_rename = renamedMethodNameByTtl(dequeueMethod);
        //int left, long deadlineNs
        final String beforeCode = "if (" + CURRENT_CAPTURED_FIELD_VALUE + " != null) {\n" +
            "  com.alibaba.ttl.TransmittableThreadLocal.Transmitter.restore(" + CURRENT_CAPTURED_FIELD_VALUE + ");\n" +
            "  " + CURRENT_CAPTURED_FIELD_VALUE + " = null;\n" +
            "}\n";

        final String tryCode = "  " + processMailBox_renamed_method_rename + "();\n";

        final String finallyCode = "if (!" + CAPTURED_FIELD_NAME + ".isEmpty()) {\n" +
            "  " + CAPTURED_FIELD_NAME + ".clear();\n" +
            "}\n";

        doTryFinallyForMethod(dequeueMethod, processMailBox_renamed_method_rename, beforeCode, tryCode, finallyCode);
    }

    public void doTryFinallyForMethod(@NonNull CtMethod method, @NonNull String renamedMethodName, @NonNull String beforeCode, String tryCode, @NonNull String finallyCode) throws CannotCompileException, NotFoundException {
        final CtClass clazz = method.getDeclaringClass();
        final CtMethod newMethod = CtNewMethod.copy(method, clazz, null);

        // rename original method, and set to private method(avoid reflect out renamed method unexpectedly)
        method.setName(renamedMethodName);
        method.setModifiers(method.getModifiers()
            & ~Modifier.PUBLIC /* remove public */
            & ~Modifier.PROTECTED /* remove protected */
            | Modifier.PRIVATE /* add private */);
        // set new method implementation
        final String code = "{\n" +
            beforeCode + "\n" +
            "try {\n" +
            "    " + tryCode + " \n" +
            "} finally {\n" +
            "    " + finallyCode + "\n" +
            "} }";
        logger.info("insert code around method " + signatureOfMethod(newMethod) + " of class " + clazz.getName() + ": " + code);
        newMethod.setBody(code);
        clazz.addMethod(newMethod);
    }


}
