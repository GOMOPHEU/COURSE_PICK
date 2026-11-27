import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

import { colors } from '@/course/ui';

export default function Layout() {
  return (
    <>
      <StatusBar style="dark" />
      <Stack screenOptions={{
        headerTintColor: colors.text,
        headerStyle: { backgroundColor: colors.bg },
        headerShadowVisible: false,
        contentStyle: { backgroundColor: colors.bg },
      }}>
        <Stack.Screen name="index" options={{ title: '코스픽' }} />
        <Stack.Screen name="survey" options={{ title: '코스픽 · 코스 취향 설정' }} />
        <Stack.Screen name="confirm" options={{ title: '입력 조건 확인' }} />
        <Stack.Screen name="recommend" options={{ title: '코스 추천' }} />
        <Stack.Screen name="courses" options={{ title: '추천 코스' }} />
        <Stack.Screen name="course/[courseId]/index" options={{ title: '코스 상세' }} />
        <Stack.Screen name="course/[courseId]/map" options={{ title: '지도와 길찾기' }} />
        <Stack.Screen name="course/[courseId]/place/[order]" options={{ title: '장소 상세' }} />
        <Stack.Screen name="course/[courseId]/replace/[order]" options={{ title: '장소 변경' }} />
      </Stack>
    </>
  );
}
