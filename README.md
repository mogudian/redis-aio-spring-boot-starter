# redis-aio-spring-boot-starter

Redis All in One 只需要一份配置即可集成Redis的多个客户端，包括 `Lettuce` 和 `Redisson` ，支持单机和多种集群配置

好处是无需配置两套相同的配置，降低一丢丢配置量

## 使用说明

- 1、集成依赖（需先将该项目源码下载并打包）

```xml

<dependency>
    <groupId>com.mogudiandian</groupId>
    <artifactId>redis-aio-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

- 2、去掉启动类中的 `RedisAutoConfiguration`

```java

@SpringBootApplication(exclude = {RedisAutoConfiguration.class})
public class StartApplication {
    public static void main(String[] args) {
        SpringApplication.run(StartApplication.class, args);
    }
}
```

- 3、集成配置，只有前缀和 `spring-data-redis` 有区别，把 `spring.redis` 换成 `redis.aio` 即可，后面的 key 相同，下面是单机和集群的配置

```properties
redis.aio.host=xxx.xxx.xxx.com
redis.aio.port=6379
redis.aio.database=1
redis.aio.password=YOUR_PASSWORD
redis.aio.timeout=60s
```

```properties
redis.aio.cluster.nodes=xxx.xxx.xxx.com:23456,xxx.xxx.xxx.com:23567
redis.aio.password=YOUR_PASSWORD
redis.aio.timeout=60s
```
- 4、按原有的方式使用

```java
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CacheService {
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    @Autowired
    private RedissonClient redissonClient;
    
    public void doYourBiz() {
        String value = stringRedisTemplate.opsForValue().get("YOUR:KEY");
        // do something...
        RLock lock = redissonClient.getLock("BIZ:UNIQUE:KEY");
        try {
            lock.lock();
            // do something...
        } finally {
            lock.unlock();
        }
    }
}
```

## 依赖三方库

| 依赖            | 版本号           | 说明  |
|---------------|---------------|-----|
| spring-boot   | 2.3.4.RELEASE |     |
| lettuce       | 5.3.4.RELEASE |     |
| redisson      | 3.15.6        |     |
| commons-lang3 | 3.11          |     |
| commons-pool2 | 2.8.1         |     |

## 使用前准备

- [Maven](https://maven.apache.org/) (构建/发布当前项目)
- Java 8 ([Download](https://adoptopenjdk.net/releases.html?variant=openjdk8))

## 构建/安装项目

使用以下命令:

`mvn clean install`

## 发布项目

修改 `pom.xml` 的 `distributionManagement` 节点，替换为自己在 `settings.xml` 中 配置的 `server` 节点，
然后执行 `mvn clean deploy`

举例：

`settings.xml`

```xml

<servers>
    <server>
        <id>snapshots</id>
        <username>yyy</username>
        <password>yyy</password>
    </server>
    <server>
        <id>releases</id>
        <username>xxx</username>
        <password>xxx</password>
    </server>
</servers>
```

`pom.xml`

```xml

<distributionManagement>
    <snapshotRepository>
        <id>snapshots</id>
        <url>http://xxx/snapshots</url>
    </snapshotRepository>
    <repository>
        <id>releases</id>
        <url>http://xxx/releases</url>
    </repository>
</distributionManagement>
```
