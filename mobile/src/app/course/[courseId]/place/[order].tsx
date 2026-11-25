import { router, useLocalSearchParams } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { isMockMode } from '@/course/api';
import { PlaceDetails } from '@/course/place-details';
import { useCourseStore } from '@/course/store';
import { Button, Copy, Heading, Screen } from '@/course/ui';

export default function PlaceScreen() {
  const { courseId, order } = useLocalSearchParams<{ courseId: string; order: string }>();
  const place = useCourseStore(state => state.result?.courses.find(course => course.courseId === courseId)
    ?.places.find(stop => stop.order === Number(order)));
  const date = useCourseStore(state => state.request.date);
  if (!place) return <Screen title="장소를 찾을 수 없어요"><Button title="코스 목록" onPress={() => router.dismissTo('/courses')} /></Screen>;

  return (
    <Screen>
      <PlaceDetails key={place.kakaoId + date} place={place} date={date} demo={isMockMode} />
      <View style={styles.stay}>
        <Heading>기준 체류시간 {place.stayMinutes}분</Heading>
        <Copy>{place.order}번째 방문 · {place.arriveAt} 도착 예정</Copy>
      </View>
      <Button title="코스로 돌아가기" arrow onPress={() => router.dismissTo({
        pathname: '/course/[courseId]', params: { courseId },
      })} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  stay: { gap: 4 },
});
