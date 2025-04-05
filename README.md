## gulimall-backend

![](https://cdn.jsdelivr.net/gh/zljin/document/img/technical/framework.jpg?raw=true)


### 前置要求

#### 安装docker-desktop

![](https://cdn.jsdelivr.net/gh/zljin/document/img/technical/dockerdesktop.png?raw=true)

安装mysql,redis,rabbitmq,elastic search,kibana

#### 开发资料

> 包含sql,nginx,jmeter脚本

https://github.com/zljin/gulimall-backend/tree/master/files



#### 人人开源工具

https://gitee.com/renrenio



#### 工具插件

idea install mybatisx插件


#### refer

https://github.com/NiceSeason/gulimall-learning/tree/master/docs  

https://gitee.com/leifengyang/gulimall  

https://www.bilibili.com/video/BV1np4y1C7Yf/?p=314&spm_id_from=pageDriver&vd_source=88f2d67f21120fbed5f365a6638870f5  



## nacos

### 服务发现

```yml
<!--        服务注册/发现-->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<!--        配置中心来做配置管理-->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

```java
@EnableDiscoveryClient
```

```yaml
spring:
  cloud:
    nacos:
      username: nacos
      password: nacos
      discovery:
        server-addr: 127.0.0.1:8848
      config:
        server-addr: 127.0.0.1:8848
        import-check:
          enabled: false
  application:
    name: gulimall-coupon
```
### 配置中心

> 文件的命名规则为：${spring.application.name}-${spring.profiles.active}.${spring.cloud.nacos.config.file-extension}

@RestController
@RefreshScope

[Nacos支持三种配置加载方方案](https://github.com/NiceSeason/gulimall-learning/blob/master/docs/%E8%B0%B7%E7%B2%92%E5%95%86%E5%9F%8E%E2%80%94%E5%88%86%E5%B8%83%E5%BC%8F%E5%9F%BA%E7%A1%80.md#4nacos%E6%94%AF%E6%8C%81%E4%B8%89%E7%A7%8D%E9%85%8D%E7%BD%AE%E5%8A%A0%E8%BD%BD%E6%96%B9%E6%96%B9%E6%A1%88)

## openfeign

!!! 在openfeign中会将调用的数据转换为JSON，接收方接收后，将JSON转换为对象，此时调用方和被调用方的处理JSON的对象不一定都是同一个类，只要它们的字段类型吻合即可

> 模拟member微服务 远程调用 coupon 微服务

在coupon微服务CouponController中新添一个接口

```java
    @RequestMapping("/member/list")
    public R memberCoupons(){
        CouponEntity couponEntity = new CouponEntity();
        couponEntity.setCouponName("discount 20%");
        return R.ok().put("coupons",Arrays.asList(couponEntity));
    }
```

在member服务中修改引入open-feign

```xml
<!--openfeign和loadbalancer要一起使用，否则报错-->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

添加上@EnableFeignClients

```java
@EnableFeignClients(basePackages = "com.zljin.gulimall.member.feign")
@EnableDiscoveryClient
@MapperScan("com.zljin.gulimall.member.dao")
@SpringBootApplication
public class GulimallMemberApplication {
    public static void main(String[] args) {
        SpringApplication.run(GulimallMemberApplication.class, args);
    }
}
```

新建CouponFeignService接口

```java
@FeignClient("gulimall_coupon")
public interface CouponFeignService {
    @RequestMapping("/coupon/coupon/member/list")
    public R memberCoupons();
}
```

远程调用功能

```java
@RequestMapping("/coupons")
public R test(){
    MemberEntity memberEntity=new MemberEntity();
    memberEntity.setNickname("zhangsan");
    R memberCoupons = couponFeignService.memberCoupons();

    return memberCoupons.put("member",memberEntity).put("coupons",memberCoupons.get("coupons"));
}
```

(4)、访问<http://localhost:11113/member/member/coupons>

## gateway

[Gateway官方文档](https://cloud.spring.io/spring-cloud-static/spring-cloud-gateway/2.2.2.RELEASE/reference/html/#gateway-request-predicates-factories)

https://github.com/zljin/gulimall-backend/blob/master/gulimall-gateway/src/main/resources/application.yml

## JSR303校验

[JSR303校验](https://github.com/NiceSeason/gulimall-learning/blob/master/docs/%E8%B0%B7%E7%B2%92%E5%95%86%E5%9F%8E%E2%80%94%E5%88%86%E5%B8%83%E5%BC%8F%E5%9F%BA%E7%A1%80.md#18-jsr303%E6%A0%A1%E9%AA%8C)

## 分组校验功能

1、给校验注解，标注上groups，指定什么情况下才需要进行校验

如：指定在更新和添加的时候，都需要进行校验

```java
	@NotEmpty
	@NotBlank(message = "品牌名必须非空",groups = {UpdateGroup.class,AddGroup.class})
	private String name;
```

在这种情况下，没有指定分组的校验注解，默认是不起作用的。想要起作用就必须要加groups。

2、业务方法参数上使用@Validated注解，并在value中给出group接口

@Validated的value方法：
指定一个或多个验证组以应用于此注释启动的验证步骤。

JSR-303 将验证组定义为自定义注释，应用程序声明的唯一目的是将它们用作类型安全组参数，如 SpringValidatorAdapter 中实现的那样。

其他SmartValidator 实现也可以以其他方式支持类参数。

3、默认情况下，在分组校验情况下，没有指定指定分组的校验注解，将不会生效，它只会在不分组的情况下生效。



## 自定义校验功能

1、编写一个自定义的校验注解

```java

@Documented
@Constraint(validatedBy = { ListValueConstraintValidator.class})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface ListValue {
    String message() default "{io.niceseason.common.valid.ListValue.message}";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };

    int[] value() default {};
}
```



2、编写一个自定义的校验器

```java

public class ListValueConstraintValidator implements ConstraintValidator<ListValue,Integer> {
    private Set<Integer> set=new HashSet<>();
    @Override
    public void initialize(ListValue constraintAnnotation) {
        int[] value = constraintAnnotation.value();
        for (int i : value) {
            set.add(i);
        }

    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {


        return  set.contains(value);
    }
}

```

3、关联自定义的校验器和自定义的校验注解

```java
@Constraint(validatedBy = { ListValueConstraintValidator.class})
```

4、使用实例

```java
	/**
	 * 显示状态[0-不显示；1-显示]
	 */
	@ListValue(value = {0,1},groups ={AddGroup.class})
	private Integer showStatus;
```



## 规格参数新增与VO

vo(view object)

po(persistant object)

to(transfer object)
## ELASTICSEARCH

https://zljin.github.io/2024/06/22/baseES/



https://github.com/zljin/gulimall-backend/tree/master/gulimall-search



注意商品上架，这里手动提交

https://github.com/zljin/gulimall-backend/blob/master/gulimall-product/src/main/java/com/zljin/gulimall/product/service/impl/SpuInfoServiceImpl.java#L238

https://github.com/zljin/gulimall-backend/blob/master/gulimall-search/README.md



https://github.com/NiceSeason/gulimall-learning/blob/master/docs/%E8%B0%B7%E7%B2%92%E5%95%86%E5%9F%8E%E2%80%94%E5%88%86%E5%B8%83%E5%BC%8F%E9%AB%98%E7%BA%A7.md#1-elasticsearch



## Nginx

反向代理配置，搭建域名访问环境，Nginx动静分类：
https://github.com/zljin/gulimall-backend/tree/master/files/nginx

直接windows一键安装即可

start nginx       #启动 nginx

nginx -s reload     #重启 nginx

nginx -s stop     #快速停止 nginx

nginx -s quit     #完整有序地停止 nginx

## 性能压测与优化

压测工具与环境

* jvisualvm,MAT

* Jmeter

  https://github.com/zljin/gulimall-backend/tree/master/files/jmeter

## 缓存

缓存数据的一致性总结：

* 我们能放入缓存的数据本就不应该是实时性、一致性要求超高的。所以缓存数据的时候加上过期时间，保
  证每天拿到当前最新数据即可。
*  我们不应该过度设计，增加系统的复杂性
*  遇到实时性、一致性要求高的数据，就应该查数据库，即使慢点。



https://github.com/NiceSeason/gulimall-learning/blob/master/docs/%E8%B0%B7%E7%B2%92%E5%95%86%E5%9F%8E%E2%80%94%E5%88%86%E5%B8%83%E5%BC%8F%E9%AB%98%E7%BA%A7.md#2-%E5%88%86%E5%B8%83%E5%BC%8F%E7%BC%93%E5%AD%98


## 异步

https://github.com/zljin/gulimall-backend/blob/master/gulimall-product/src/main/java/com/zljin/gulimall/product/service/impl/SkuInfoServiceImpl.java#L139



https://github.com/NiceSeason/gulimall-learning/blob/master/docs/%E8%B0%B7%E7%B2%92%E5%95%86%E5%9F%8E%E2%80%94%E5%88%86%E5%B8%83%E5%BC%8F%E9%AB%98%E7%BA%A7.md#%E5%BC%82%E6%AD%A5


## 认证服务

### 社交登录oauth2.0

![](img/oauth.png)

[read://https_blog.csdn.net/?url=https%3A%2F%2Fblog.csdn.net%2Fqq_38225558%2Farticle%2Fdetails%2F85258837](read://https_blog.csdn.net/?url=https%3A%2F%2Fblog.csdn.net%2Fqq_38225558%2Farticle%2Fdetails%2F85258837)



https://github.com/zljin/gulimall-backend/commit/6801b654e33f6f39142e98eefd16bdb8cad07d88

### SpringSession整合redis

导入依赖

```xml
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
```

修改配置

```yaml
spring:
  redis:
    host: 192.168.56.102
  session:
    store-type: redis
```

2) 自定义配置

* 由于默认使用jdk进行序列化，通过导入`RedisSerializer`修改为json序列化

* 并且通过修改`CookieSerializer`扩大`session`的作用域至`**.gulimall.com`

```java
@Configuration
@EnableRedisHttpSession
public class GulimallSessionConfig {

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("GULISESSIONID");
        serializer.setDomainName("gulimall.com");
        return serializer;
    }
}
```

### ThreadLocal用户身份鉴别

#### (1) 用户身份鉴别方式

参考京东，在点击购物车时，会为临时用户生成一个`name`为`user-key`的`cookie`临时标识，过期时间为一个月，如果手动清除`user-key`，那么临时购物车的购物项也被清除，所以`user-key`是用来标识和存储临时购物车数据的

#### (2) 使用ThreadLocal进行用户身份鉴别信息传递

* 在调用购物车的接口前，先通过session信息判断是否登录，并分别进行用户身份信息的封装，并把`user-key`放在cookie中
* 这个功能使用拦截器进行完成

```java
@Configuration
public class GulimallWebConfig implements WebMvcConfigurer {
    //拦截所有请求
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CartInterceptor()).addPathPatterns("/**");
    }
}

public class CartInterceptor implements HandlerInterceptor {

    public static ThreadLocal<UserInfoTo> threadLocal=new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        MemberResponseVo memberResponseVo = (MemberResponseVo) session.getAttribute(AuthServerConstant.LOGIN_USER);
        UserInfoTo userInfoTo = new UserInfoTo();
        //1 用户已经登录，设置userId
        if (memberResponseVo!=null){
            userInfoTo.setUserId(memberResponseVo.getId());
        }

        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            //2 如果cookie中已经有user-Key，则直接设置
            if (cookie.getName().equals(CartConstant.TEMP_USER_COOKIE_NAME)) {
                userInfoTo.setUserKey(cookie.getValue());
                userInfoTo.setTempUser(true);
            }
        }

        //3 如果cookie没有user-key，我们通过uuid生成user-key
        if (StringUtils.isEmpty(userInfoTo.getUserKey())) {
            String uuid = UUID.randomUUID().toString();
            userInfoTo.setUserKey(uuid);
        }

        //4 将用户身份认证信息放入threadlocal进行传递
        threadLocal.set(userInfoTo);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        UserInfoTo userInfoTo = threadLocal.get();
        //如果cookie中没有user-key，我们为其生成
        if (!userInfoTo.getTempUser()) {
            Cookie cookie = new Cookie(CartConstant.TEMP_USER_COOKIE_NAME, userInfoTo.getUserKey());
            cookie.setDomain("gulimall.com");
            cookie.setMaxAge(CartConstant.TEMP_USER_COOKIE_TIMEOUT);
            response.addCookie(cookie);
        }
    }
}
```

## 订单服务

### 订单流程

订单生成 -> 支付订单 -> 卖家发货 -> 确认收货 -> 交易成功



### 订单登录拦截

因为订单系统必然涉及到用户信息，因此进入订单系统的请求必须是已经登录的，所以我们需要通过拦截器对未登录订单请求进行拦截

```java
public class LoginInterceptor implements HandlerInterceptor {
    public static ThreadLocal<MemberResponseVo> loginUser = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        MemberResponseVo memberResponseVo = (MemberResponseVo) session.getAttribute(AuthServerConstant.LOGIN_USER);
        if (memberResponseVo != null) {
            loginUser.set(memberResponseVo);
            return true;
        }else {
            session.setAttribute("msg","请先登录");
            response.sendRedirect("http://auth.gulimall.com/login.html");
            return false;
        }
    }
}

@Configuration
public class GulimallWebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor()).addPathPatterns("/**");
    }
}
```

### Feign远程调用丢失请求头问题

`feign`远程调用的请求头中没有含有`JSESSIONID`的`cookie`，所以也就不能得到服务端的`session`数据，cart认为没登录，获取不了用户信息

但是在`feign`的调用过程中，会使用容器中的`RequestInterceptor`对`RequestTemplate`进行处理，因此我们可以通过向容器中导入定制的`RequestInterceptor`为请求加上`cookie`。

```java
public class GuliFeignConfig {
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                //1. 使用RequestContextHolder拿到老请求的请求数据
                ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (requestAttributes != null) {
                    HttpServletRequest request = requestAttributes.getRequest();
                    if (request != null) {
                        //2. 将老请求得到cookie信息放到feign请求上
                        String cookie = request.getHeader("Cookie");
                        template.header("Cookie", cookie);
                    }
                }
            }
        };
    }
}
```

* `RequestContextHolder`为SpingMVC中共享`request`数据的上下文，底层由`ThreadLocal`实现

* 由于`RequestContextHolder`使用`ThreadLocal`共享数据，所以在开启异步时获取不到老请求的信息，自然也就无法共享`cookie`了

在这种情况下，我们需要在开启异步的时候将老请求的`RequestContextHolder`的数据设置进去

https://github.com/zljin/gulimall-backend/blob/master/gulimall-order/src/main/java/com/zljin/gulimall/order/service/impl/OrderServiceImpl.java#L105

and

https://github.com/zljin/gulimall-backend/blob/master/gulimall-order/src/main/java/com/zljin/gulimall/order/service/impl/OrderServiceImpl.java#L114

### 使用消息队列实现最终一致性


![](https://cdn.jsdelivr.net/gh/zljin/document/img/technical/orderstockflow.png?raw=true)


## 秒杀服务

> 秒杀商品上架

![](https://cdn.jsdelivr.net/gh/zljin/document/img/technical/seckillupflow.png?raw=true)

> 秒杀flow

![](https://cdn.jsdelivr.net/gh/zljin/document/img/technical/seckillflow.png?raw=true)



