import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Linking } from 'react-native';

import { startPoints, transportLabels } from '@/course/contracts';
import CourseMap from '@/course/course-map';
import { useCourseStore } from '@/course/store';
import { Button, Card, Copy, ErrorText, Screen } from '@/course/ui';

export default function MapScreen() {
  const { courseId } = useLocalSearchParams<{ courseId: string }>();
  const course = useCourseStore(state => state.result?.courses.find(value => value.courseId === courseId));
  const origin = useCourseStore(state => startPoints.find(point => point.id === state.request.startPointId));
  const [linkError, setLinkError] = useState(false);
  if (!course || !origin) return <Screen title="코스를 찾을 수 없어요"><Button title="설문으로" onPress={() => router.dismissTo('/survey')} /></Screen>;

  return (
    <Screen title="코스 지도" subtitle="선은 방문 순서를 보여주는 직선입니다.">
      <CourseMap origin={origin} course={course} />
      <Card><Copy>{[origin, ...course.places].map(point => point.name).join(' → ')}</Copy></Card>
      {course.legs.map(leg => {
        const from = leg.from === 'start' ? origin : course.places.find(place => place.order === leg.from);
        const to = course.places.find(place => place.order === leg.to);
        if (!from || !to) return null;
        const query = `sp=${from.lat},${from.lng}&ep=${to.lat},${to.lng}&by=${leg.mode === 'walk' ? 'foot' : 'publictransit'}`;
        return <Card key={leg.to}>
          <Copy>{from.name} → {to.name} · {transportLabels[leg.mode]} {leg.minutes}분</Copy>
          <Button title="카카오맵 길찾기" secondary onPress={async () => {
            setLinkError(false);
            try {
              await Linking.openURL('kakaomap://route?' + query);
            } catch {
              try { await Linking.openURL('https://m.map.kakao.com/scheme/route?' + query); }
              catch { setLinkError(true); }
            }
          }} />
        </Card>;
      })}
      {linkError && <ErrorText message="카카오맵을 열지 못했어요. 구간을 선택해 다시 시도해 주세요." />}
    </Screen>
  );
}
