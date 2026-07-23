package com.sky.aspect;

import com.sky.anotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面类，实现公共字段自动填充处理逻辑
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    /**
     * 切入点：mapper包下所有方法 && 贴了@AutoFill注解的方法
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.anotation.AutoFill)")
    public void autoFillPointCut() {
    }

    /**
     * 前置通知，在目标方法执行前自动填充公共字段
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始进行公共字段自动填充...");

        // 1. 获取被拦截方法的 MethodSignature
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // 2. 拿到方法上的 @AutoFill 注解
        Method method = signature.getMethod();
        AutoFill autoFill = method.getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();  // INSERT 或 UPDATE

        // 3. 拿到方法的第一个参数，也就是实体对象(employee/category等)
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        Object entity = args[0];   // 比如 Employee 对象、Category 对象

        // 4. 准备要填充的值
        LocalDateTime now = LocalDateTime.now();
        Long currentUserId = BaseContext.getCurrentId();

        // 5. 根据操作类型填充不同的字段
        if (operationType == OperationType.INSERT) {
            // 插入：填4个字段
            try {
                entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class)
                        .invoke(entity, now);
                entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class)
                        .invoke(entity, now);
                entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class)
                        .invoke(entity, currentUserId);
                entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class)
                        .invoke(entity, currentUserId);
            } catch (Exception e) {
                log.error("公共字段自动填充失败", e);
            }
        } else if (operationType == OperationType.UPDATE) {
            // 修改：只填2个字段
            try {
                entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class)
                        .invoke(entity, now);
                entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class)
                        .invoke(entity, currentUserId);
            } catch (Exception e) {
                log.error("公共字段自动填充失败", e);
            }
        }
    }
}
