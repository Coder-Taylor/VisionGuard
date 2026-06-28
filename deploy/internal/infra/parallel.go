package infra

import (
	"context"
	"runtime"
	"sync"
)

// ParallelMap 将 items 分片后并行执行 fn，返回与输入顺序一致的结果。
// 适用于 CPU 密集的批量计算（如统计聚合、数据清洗）。
// workers 控制最大并行 goroutine 数，默认 runtime.GOMAXPROCS(0)。
// 任一 fn 返回错误则立即取消并返回该错误（先胜错误模型）。
func ParallelMap[T any, R any](ctx context.Context, items []T, fn func(context.Context, T) (R, error), workers ...int) ([]R, error) {
	if len(items) == 0 {
		return nil, nil
	}

	// 确定并发数
	n := runtime.GOMAXPROCS(0)
	if len(workers) > 0 && workers[0] > 0 {
		n = workers[0]
	}
	if n <= 0 {
		n = 1
	}
	if n > len(items) {
		n = len(items)
	}

	type unit struct {
		idx int
		val R
		err error
	}

	// 分片
	chunkSize := (len(items) + n - 1) / n
	ch := make(chan unit, len(items))

	ctx, cancel := context.WithCancelCause(ctx)
	defer cancel(nil)

	var wg sync.WaitGroup
	for i := 0; i < n; i++ {
		lo := i * chunkSize
		hi := lo + chunkSize
		if hi > len(items) {
			hi = len(items)
		}
		if lo >= hi {
			break
		}
		wg.Add(1)
		go func(batch []T, offset int) {
			defer wg.Done()
			for j, item := range batch {
				select {
				case <-ctx.Done():
					ch <- unit{idx: offset + j, err: context.Cause(ctx)}
					return
				default:
				}
				v, err := fn(ctx, item)
				ch <- unit{idx: offset + j, val: v, err: err}
			}
		}(items[lo:hi], lo)
	}

	// 所有 worker 完成后关闭 ch
	go func() {
		wg.Wait()
		close(ch)
	}()

	results := make([]R, len(items))
	for u := range ch {
		if u.err != nil {
			cancel(u.err)
			// 消费剩余 channel 防止 worker 泄漏
			go func() {
				for range ch {
				}
			}()
			return nil, u.err
		}
		results[u.idx] = u.val
	}
	return results, nil
}

// ParallelForEach 是 ParallelMap 的简化版，只执行副作用不收集返回值。
func ParallelForEach[T any](ctx context.Context, items []T, fn func(context.Context, T) error, workers ...int) error {
	_, err := ParallelMap(ctx, items, func(ctx context.Context, t T) (struct{}, error) {
		return struct{}{}, fn(ctx, t)
	}, workers...)
	return err
}
