package cn.nobeta.bbs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cn.nobeta.bbs.config.ElasticsearchIndexInitializer;
import cn.nobeta.bbs.task.AvatarCleanupTask;
import cn.nobeta.bbs.task.ConsistencyReconciliationTask;
import cn.nobeta.bbs.task.OutboxCleanupTask;
import cn.nobeta.bbs.task.OutboxPublisherTask;
import cn.nobeta.bbs.task.RedisOutboxPublisherTask;

@SpringBootTest
@ActiveProfiles("test")
// 验证应用装配；外部服务初始化及后台任务由各自测试验证。
@MockitoBean(types = {
    ElasticsearchIndexInitializer.class,
    AvatarCleanupTask.class,
    ConsistencyReconciliationTask.class,
    OutboxCleanupTask.class,
    OutboxPublisherTask.class,
    RedisOutboxPublisherTask.class
}, enforceOverride = true)
class BbsApplicationTests {

	@Test
	void contextLoads() {
	}

}
