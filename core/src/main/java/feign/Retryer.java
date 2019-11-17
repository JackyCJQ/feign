/**
 * Copyright 2012-2019 The Feign Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Cloned for each invocation to {@link Client#execute(Request, feign.Request.Options)}.
 * Implementations may keep state to determine if retry operations should continue or not.
 */
public interface Retryer extends Cloneable {

    /**
     * if retry is permitted, return (possibly after sleeping). Otherwise propagate the exception.
     */
    void continueOrPropagate(RetryableException e);

    Retryer clone();

    public static class Default implements Retryer {

        private final int maxAttempts;
        private final long period;
        private final long maxPeriod;
        int attempt; //已经尝试的次数
        long sleptForMillis;//已经休眠的时间数

        /**
         * 设置默认值
         */
        public Default() {
            this(100, SECONDS.toMillis(1), 5);
        }

        public Default(long period, long maxPeriod, int maxAttempts) {
            this.period = period;
            this.maxPeriod = maxPeriod;
            this.maxAttempts = maxAttempts;
            this.attempt = 1; //默认设置为已经尝试的次数
        }

        //系统当前时间
        protected long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        //继续或者是跳过
        public void continueOrPropagate(RetryableException e) {
            //如果已经大于了最大的尝试次数 则就抛出了异常
            if (attempt++ >= maxAttempts) {
                throw e;
            }

            long interval;
            //这里直接获取属性 不是更块吗？
            if (e.retryAfter() != null) {

                interval = e.retryAfter().getTime() - currentTimeMillis();
                if (interval > maxPeriod) {
                    interval = maxPeriod;
                }
                if (interval < 0) {
                    return;
                }
            } else {
                interval = nextMaxInterval();
            }
            try {
                //当前线程开始休眠
                Thread.sleep(interval);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                throw e;
            }
            sleptForMillis += interval;
        }

        /**
         * Calculates the time interval to a retry attempt. <br>
         * The interval increases exponentially with each attempt, at a rate of nextInterval *= 1.5
         * (where 1.5 is the backoff factor), to the maximum interval.
         *
         * @return time in nanoseconds from now until the next attempt.
         */
        long nextMaxInterval() {
            long interval = (long) (period * Math.pow(1.5, attempt - 1));
            return interval > maxPeriod ? maxPeriod : interval;
        }

        @Override
        public Retryer clone() {
            return new Default(period, maxPeriod, maxAttempts);
        }
    }

    /**
     * Implementation that never retries request. It propagates the RetryableException.
     */
    Retryer NEVER_RETRY = new Retryer() {

        @Override
        public void continueOrPropagate(RetryableException e) {
            throw e;
        }

        @Override
        public Retryer clone() {
            return this;
        }
    };
}
