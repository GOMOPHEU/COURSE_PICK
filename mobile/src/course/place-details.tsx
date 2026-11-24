import { useEffect, useState } from 'react';
import { ActivityIndicator, Image } from 'react-native';

import { getPlaceDetails } from './api';
import { colors, Copy, ErrorText } from './ui';

export function PlaceDetails({ kakaoId, date }: { kakaoId: string; date: string }) {
  const [details, setDetails] = useState<Awaited<ReturnType<typeof getPlaceDetails>> | null>(null);
  const [photoFailed, setPhotoFailed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort('timeout'), 15000);
    let active = true;

    getPlaceDetails(kakaoId, date, controller.signal)
      .then(value => { if (active) setDetails(value); })
      .catch(cause => {
        if (!active) return;
        setError(controller.signal.aborted ? '장소 상세 응답이 지연됐어요.'
          : cause instanceof Error ? cause.message : '장소 상세 정보를 불러오지 못했어요.');
      })
      .finally(() => clearTimeout(timeout));

    return () => {
      active = false;
      clearTimeout(timeout);
      controller.abort();
    };
  }, [kakaoId, date]);

  if (error) return <ErrorText message={error} />;
  if (!details) return <ActivityIndicator color={colors.blue} accessibilityLabel="장소 상세 불러오는 중" />;
  return (
    <>
      {details.photoUrl && !photoFailed ? <Image source={{ uri: details.photoUrl }} accessibilityLabel="장소 사진"
        style={{ width: '100%', height: 180 }} onError={() => setPhotoFailed(true)} /> : <Copy>장소 사진 미제공</Copy>}
      <Copy>{details.address ? '주소 (카카오) · ' + details.address : '주소 미확인'}</Copy>
      {details.openingHours && <Copy>영업시간 {details.openingHours}</Copy>}
      {details.rating !== undefined && <Copy>별점 {details.rating}</Copy>}
      {(details.openingHours || details.rating !== undefined) && <Copy>Google Maps</Copy>}
      {!details.openingHours && details.rating === undefined && <Copy>제공된 영업시간과 별점이 없어요.</Copy>}
    </>
  );
}
