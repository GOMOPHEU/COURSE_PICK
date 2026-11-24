import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator } from 'react-native';

import { recommend } from '@/course/api';
import { validate } from '@/course/schedule';
import { useCourseStore } from '@/course/store';
import { Button, colors, ErrorText, Screen } from '@/course/ui';

export default function Recommend() {
  const input = useCourseStore(state => state.request);
  const setResult = useCourseStore(state => state.setResult);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const cancelled = useRef(false);
  const invalid = validate(input);

  useEffect(() => {
    if (invalid) return;
    const controller = new AbortController();
    cancelled.current = false;
    let active = true;
    const timeout = setTimeout(() => controller.abort(), 20000);
    recommend(input, controller.signal)
      .then(result => {
        if (!active || cancelled.current) return;
        setResult(result);
        router.replace('/courses');
      })
      .catch(cause => {
        if (!active || cancelled.current) return;
        setError(controller.signal.aborted ? '추천 응답이 지연됐어요. 다시 시도해 주세요.'
          : cause instanceof Error ? cause.message : '추천 요청을 완료할 수 없어요.');
      })
      .finally(() => clearTimeout(timeout));
    return () => {
      active = false;
      clearTimeout(timeout);
      controller.abort();
    };
  }, [input, setResult, attempt, invalid]);

  if (invalid || error) {
    return (
      <Screen title="추천을 진행할 수 없어요">
        <ErrorText message={invalid ?? error ?? ''} />
        {!invalid && <Button title="다시 시도" onPress={() => { setError(null); setAttempt(value => value + 1); }} />}
        <Button title="조건 수정" secondary onPress={() => router.dismissTo('/survey')} />
      </Screen>
    );
  }

  return (
    <Screen title="코스를 찾고 있어요" subtitle="선택한 테마와 이용 가능 시간에 맞는 일정을 만들고 있어요.">
      <ActivityIndicator color={colors.blue} size="large" accessibilityLabel="추천 진행 중" />
      <Button title="추천 취소" secondary onPress={() => {
        cancelled.current = true;
        router.dismissTo('/confirm');
      }} />
    </Screen>
  );
}
