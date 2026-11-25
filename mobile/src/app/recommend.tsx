import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { recommend } from '@/course/api';
import { ConditionSummary } from '@/course/course-summary';
import { SeoulIllustration } from '@/course/seoul-illustration';
import { validate } from '@/course/schedule';
import { useCourseStore } from '@/course/store';
import { Button, colors, Copy, ErrorText, Heading, Screen } from '@/course/ui';

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
    <Screen style={styles.page}>
      <View style={styles.content}>
        <View style={styles.art}>
          <SeoulIllustration />
          <View style={styles.spinner}>
            <ActivityIndicator color={colors.primary} size="large" accessibilityLabel="추천 진행 중" />
          </View>
        </View>
      </View>
      <View style={styles.message}>
        <Heading>취향에 맞는 하루를 만들고 있어요</Heading>
        <Copy>장소와 이동 시간을 확인하며 방문 순서를 찾고 있어요.</Copy>
      </View>
      <ConditionSummary request={input} />
      <Button title="취소하고 조건 확인" secondary onPress={() => {
        cancelled.current = true;
        router.dismissTo('/confirm');
      }} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  page: { justifyContent: 'center', gap: 20 },
  content: { alignItems: 'center' },
  art: { width: '86%', maxWidth: 360, justifyContent: 'center', alignItems: 'center' },
  spinner: { position: 'absolute', backgroundColor: colors.surface, borderRadius: 26, padding: 8 },
  message: { alignItems: 'center', gap: 6 },
});
