package infra

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// DistributedLock — Redis 分布式锁（防止多设备同时写入同一记录）
type DistributedLock struct {
	rdb  *redis.Client
	key  string
	val  string
	ttl  time.Duration
}

// NewLock 创建分布式锁
func NewLock(rdb *redis.Client, resource string, id string, ttl time.Duration) *DistributedLock {
	return &DistributedLock{
		rdb: rdb,
		key: fmt.Sprintf("lock:%s", resource),
		val: id,
		ttl: ttl,
	}
}

// Acquire 获取锁（非阻塞）
func (l *DistributedLock) Acquire(ctx context.Context) (bool, error) {
	return l.rdb.SetNX(ctx, l.key, l.val, l.ttl).Result()
}

// Release 释放锁
func (l *DistributedLock) Release(ctx context.Context) error {
	// Lua 脚本：只有持有者才能释放
	script := `
		if redis.call("GET", KEYS[1]) == ARGV[1] then
			return redis.call("DEL", KEYS[1])
		else
			return 0
		end
	`
	return l.rdb.Eval(ctx, script, []string{l.key}, l.val).Err()
}

// WithLock 在锁保护下执行函数
func WithLock(rdb *redis.Client, resource string, id string, ttl time.Duration, fn func() error) error {
	lock := NewLock(rdb, resource, id, ttl)
	ctx := context.Background()

	ok, err := lock.Acquire(ctx)
	if err != nil {
		return fmt.Errorf("lock acquire error: %w", err)
	}
	if !ok {
		return fmt.Errorf("resource busy, please retry")
	}
	defer lock.Release(ctx)

	return fn()
}
