import { router, useLocalSearchParams } from 'expo-router';

import { isMockMode } from '@/course/api';
import { themes } from '@/course/contracts';
import { PlaceDetails } from '@/course/place-details';
import { useCourseStore } from '@/course/store';
import { Button, Card, Copy, Screen } from '@/course/ui';

export default function PlaceScreen() {
  const { courseId, order } = useLocalSearchParams<{ courseId: string; order: string }>();
  const place = useCourseStore(state => state.result?.courses.find(course => course.courseId === courseId)
    ?.places.find(stop => stop.order === Number(order)));
  const date = useCourseStore(state => state.request.date);
  if (!place) return <Screen title="장소를 찾을 수 없어요"><Button title="코스 목록" onPress={() => router.dismissTo('/courses')} /></Screen>;

  return (
    <Screen title={place.name} subtitle={themes[place.theme] + ' · ' + date}>
      <Card>
        <Copy>{place.order}번째 방문 · {place.arriveAt} 도착 · 체류 {place.stayMinutes}분</Copy>
        <Copy>{{ confirmed: '영업 확인', official: '공식 안내 기준', unknown: '운영정보 미확인' }[place.openStatus]}</Copy>
      </Card>
      <Card>{isMockMode ? <Copy>시연용 장소 · 사진, 주소, 영업시간, 별점 미확인</Copy>
        : <PlaceDetails key={place.kakaoId + date} kakaoId={place.kakaoId} date={date} />}</Card>
    </Screen>
  );
}
