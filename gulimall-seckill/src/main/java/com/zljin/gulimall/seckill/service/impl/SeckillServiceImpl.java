package com.zljin.gulimall.seckill.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.zljin.gulimall.common.utils.R;
import com.zljin.gulimall.seckill.feign.CouponFeignService;
import com.zljin.gulimall.seckill.feign.ProductFeignService;
import com.zljin.gulimall.seckill.service.SeckillService;
import com.zljin.gulimall.seckill.to.SecKillSkuRedisTo;
import com.zljin.gulimall.seckill.vo.SeckillSesssionsWithSkus;
import com.zljin.gulimall.seckill.vo.SkuInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import cn.hutool.core.lang.TypeReference;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    ProductFeignService productFeignService;

    private final String SESSIONS_CACHE_PREFIX = "seckill:sessions:";
    private final String SKUKILL_CACHE_PREFIX = "seckill:skus";
    private final String SKU_STOCK_SEMAPHORE = "seckill:stock:";//+商品随机码

    /**
     * 扫描最近三天需要参与秒杀的活动进行上架并缓存到redis
     * guli_sms.sms_seckill_session (秒杀活动场次)
     * guli_sms.sms_seckill_sku (秒杀活动商品关联)
     */
    @Override
    public void uploadSeckillSkuLatest3Days() {
        R session = couponFeignService.getLates3DaySession();
        if (session.getCode() == 0) {
            List<SeckillSesssionsWithSkus> sessionData = session.getData(new TypeReference<List<SeckillSesssionsWithSkus>>() {
            });
            //1、缓存活动信息
            saveSessionInfos(sessionData);
            //2、缓存活动的关联商品信息
            saveSessionSkuInfos(sessionData);
        }
    }

    @Override
    public List<SecKillSkuRedisTo> getCurrentSeckillSkus() {
        //1、确定当前时间属于哪个秒杀场次。
        long time = new Date().getTime();
        Set<String> keys = redisTemplate.keys(SESSIONS_CACHE_PREFIX + "*");
        for (String key : keys) {
            //seckill:sessions:1725588000000_1727666365000
            String replace = key.replace(SESSIONS_CACHE_PREFIX, "");
            String[] s = replace.split("_");
            Long start = Long.parseLong(s[0]);
            Long end = Long.parseLong(s[1]);
            if (time >= start && time <= end) {
                //2、获取这个秒杀场次需要的所有商品信息
                List<String> range = redisTemplate.opsForList().range(key, -100, 100);
                BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
                List<String> list = hashOps.multiGet(range);
                if (list != null) {
                    return list.stream().map(item -> {
                        return JSONUtil.toBean(item, SecKillSkuRedisTo.class);
//                        redis.setRandomCode(null); 当前秒杀开始就需要随机码
                    }).collect(Collectors.toList());
                }
                break;
            }
            return null;
        }
        return null;
    }

    @Override
    public SecKillSkuRedisTo getSkuSeckillInfo(Long skuId) {
        //1、找到所有需要参与秒杀的商品的key
        BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        Set<String> keys = hashOps.keys();
        if(CollectionUtil.isEmpty(keys)){
            return null;
        }
        String regx = "\\d_" + skuId;
        for (String key : keys) {
            //6_4
            if (Pattern.matches(regx, key)) {
                String json = hashOps.get(key);
                SecKillSkuRedisTo skuRedisTo = JSONUtil.toBean(json, SecKillSkuRedisTo.class);
                if (skuRedisTo == null) return null;
                long current = new Date().getTime();
                if (current >= skuRedisTo.getStartTime() && current <= skuRedisTo.getEndTime()) {
                    return skuRedisTo;
                } else {
                    //当前商品已经过了秒杀时间要直接删除
                    hashOps.delete(key);
                    skuRedisTo.setRandomCode(null);
                }
            }
        }
        return null;
    }

    private void saveSessionInfos(List<SeckillSesssionsWithSkus> sessionData) {
        if (sessionData == null) {
            return;
        }
        sessionData.stream().forEach(sesssion -> {
            Long startTime = sesssion.getStartTime().getTime();
            Long endTime = sesssion.getEndTime().getTime();
            String key = SESSIONS_CACHE_PREFIX + startTime + "_" + endTime;
            Boolean hasKey = redisTemplate.hasKey(key);
            if (!hasKey) {
                List<String> collect = sesssion.getRelationSkus().stream()
                        .map(item -> item.getPromotionSessionId() + "_" + item.getSkuId().toString()).collect(Collectors.toList());
                //缓存活动信息
                redisTemplate.opsForList().leftPushAll(key, collect);
                //设置过期时间[已完成]
                redisTemplate.expireAt(key, new Date(endTime));
            }
        });
    }

    private void saveSessionSkuInfos(List<SeckillSesssionsWithSkus> sesssions) {
        if (sesssions != null)
            sesssions.stream().forEach(sesssion -> {
                //准备hash操作
                BoundHashOperations<String, Object, Object> ops = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
                sesssion.getRelationSkus().stream().forEach(seckillSkuVo -> {

                    if (!ops.hasKey(seckillSkuVo.getPromotionSessionId().toString() + "_" + seckillSkuVo.getSkuId().toString())) {
                        //缓存商品
                        SecKillSkuRedisTo redisTo = new SecKillSkuRedisTo();
                        //1、sku的基本数据
                        R skuInfo = productFeignService.getSkuInfo(seckillSkuVo.getSkuId());
                        if (skuInfo.getCode() == 0) {
                            SkuInfoVo info = skuInfo.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                            });
                            redisTo.setSkuInfo(info);
                        }

                        //2、sku的秒杀信息
                        BeanUtil.copyProperties(seckillSkuVo, redisTo);

                        //3、设置上当前商品的秒杀时间信息
                        redisTo.setStartTime(sesssion.getStartTime().getTime());
                        redisTo.setEndTime(sesssion.getEndTime().getTime());

                        //4、随机码  seckill?skuId=1&key=dadlajldj
                        /**
                         * @Leoanrd 这里的随机码相当用户一个令牌,只要到秒杀时间了,你拿到了这个令牌,你才能在信号量里面去扣减库存
                         */
                        String token = UUID.randomUUID().toString().replace("-", "");
                        redisTo.setRandomCode(token);
                        String jsonString = JSONUtil.toJsonStr(redisTo);
                        //TODO 每个商品的过期时间不一样。所以，我们在获取当前商品秒杀信息的时候，做主动删除，代码在 getSkuSeckillInfo 方法里面
                        ops.put(seckillSkuVo.getPromotionSessionId().toString() + "_" + seckillSkuVo.getSkuId().toString(), jsonString);
                        //如果当前这个场次的商品的库存信息已经上架就不需要上架
                        //5、使用库存作为分布式的信号量  限流；
                        RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                        //商品可以秒杀的数量作为信号量
                        semaphore.trySetPermits(seckillSkuVo.getSeckillCount());
                        //设置过期时间。
                        semaphore.expireAt(sesssion.getEndTime());
                    }
                });
            });
    }


}
