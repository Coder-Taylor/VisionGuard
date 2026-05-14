package infra

import (
	"context"
	"sync"
)

// Concurrent 并发执行多个 IO 任务，收集所有错误。
// 适用于 IO 密集场景（多个 DB 查询、Redis 操作、外部 API 调用）。
type Concurrent struct {
	max int
	sem chan struct{} // 信号量，控制最大并发数
	wg  sync.WaitGroup
	mu  sync.Mutex
	err []error
}

// NewConcurrent 创建一个并发执行器。
// maxConcurrent 限制最大并发 goroutine 数；0 或负数表示不限制。
func NewConcurrent(maxConcurrent int) *Concurrent {
	c := &Concurrent{max: maxConcurrent}
	if maxConcurrent > 0 {
		c.sem = make(chan struct{}, maxConcurrent)
	}
	return c
}

// Go 提交一个 IO 任务异步执行。
// 若设置了并发上限且已满，会阻塞直到有空闲槽位。
func (c *Concurrent) Go(fn func() error) {
	c.wg.Add(1)
	if c.sem != nil {
		c.sem <- struct{}{}
	}
	go func() {
		defer c.wg.Done()
		if c.sem != nil {
			defer func() { <-c.sem }()
		}
		if err := fn(); err != nil {
			c.mu.Lock()
			c.err = append(c.err, err)
			c.mu.Unlock()
		}
	}()
}

// Wait 等待所有已提交任务完成，返回收集到的所有错误（非截断）。
func (c *Concurrent) Wait() []error {
	c.wg.Wait()
	return c.err
}

// WaitFirst 等待所有已提交任务完成，仅返回第一个错误（若有）。
// 适合"任一失败即视为整体失败"的场景。
func (c *Concurrent) WaitFirst() error {
	c.wg.Wait()
	if len(c.err) > 0 {
		return c.err[0]
	}
	return nil
}

// ---- 以下为函数式便利封装 ----

// ConcurrentRun 快捷方式：创建执行器 → 提交全部任务 → 等待返回所有错误。
// maxConcurrent 控制最大并发。
func ConcurrentRun(maxConcurrent int, fns ...func() error) []error {
	g := NewConcurrent(maxConcurrent)
	for _, fn := range fns {
		g.Go(fn)
	}
	return g.Wait()
}

// ConcurrentRunCtx 支持 context 取消的版本。
// ctx 取消后所有已启动或已提交的任务仍会运行至完成；未开始的任务会通过 fn 内的 ctx 感知取消，立即返回 ctx.Err()。
func ConcurrentRunCtx(ctx context.Context, maxConcurrent int, fns ...func(context.Context) error) []error {
	g := NewConcurrent(maxConcurrent)
	for _, fn := range fns {
		fn := fn // capture
		g.Go(func() error {
			select {
			case <-ctx.Done():
				return ctx.Err()
			default:
				return fn(ctx)
			}
		})
	}
	return g.Wait()
}
