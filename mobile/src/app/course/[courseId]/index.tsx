import { router, useLocalSearchParams } from 'expo-router';

import { startPoints, themes, transportLabels } from '@/course/contracts';
import { minutes, timeAt } from '@/course/schedule';
import { useCourseStore } from '@/course/store';
import { Button, Card, Copy, Heading, Screen } from '@/course/ui';

const openStatus = { confirmed: '영업 확인', official: '공식 안내 기준', unknown: '운영정보 미확인' };

export default function CourseDetail() {
  const { courseId } = useLocalSearchParams<{ courseId: string }>();
  const course = useCourseStore(state => state.result?.courses.find(value => value.courseId === courseId));
  const request = useCourseStore(state => state.request);
  const origin = startPoints.find(point => point.id === request.startPointId);
  const available = minutes(request.endTime) - minutes(request.startTime);
  if (!course) return <Screen title="코스를 찾을 수 없어요"><Button title="설문으로" onPress={() => router.dismissTo('/survey')} /></Screen>;

  return (
    <Screen title="코스 시간표" subtitle={course.reason}>
      <Card>
        <Heading>{request.startTime} · {origin?.name} 출발</Heading>
        <Copy>예상 종료 {course.endAt} · 총 {course.totalMinutes}분 · 귀가 이동 제외</Copy>
        <Copy>{request.date} · 이용 가능 {available}분 · 남은 시간 {available - course.totalMinutes}분</Copy>
        <Copy>{course.fit === 'relaxed' ? '시간 여유' : '시간 빠듯함'}</Copy>
      </Card>
      {course.places.map(place => {
        const leg = course.legs.find(item => item.to === place.order);
        return (
          <Card key={place.order}>
            {leg && <Copy>{transportLabels[leg.mode]} {leg.minutes}분 이동</Copy>}
            <Heading>{place.order}. {place.name}</Heading>
            <Copy>{place.arriveAt}~{timeAt(minutes(place.arriveAt) + place.stayMinutes)} · 체류 {place.stayMinutes}분</Copy>
            <Copy>{themes[place.theme]} · {openStatus[place.openStatus]}</Copy>
            <Button title="장소 상세 보기" secondary onPress={() => router.push({
              pathname: '/course/[courseId]/place/[order]', params: { courseId, order: String(place.order) },
            })} />
          </Card>
        );
      })}
      <Button title="지도에서 보기" onPress={() => router.push({
        pathname: '/course/[courseId]/map', params: { courseId: course.courseId },
      })} />
    </Screen>
  );
}
