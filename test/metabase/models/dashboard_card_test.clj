(ns metabase.models.dashboard-card-test
  (:require [expectations :refer :all]
            [metabase.db :as db]
            [metabase.models.card :refer [Card]]
            [metabase.models.dashboard :refer [Dashboard]]
            [metabase.models.dashboard-card :refer :all]
            [metabase.models.dashboard-card-series :refer [DashboardCardSeries]]
            [metabase.models.hydrate :refer :all]
            [metabase.test.data :refer :all]
            [metabase.test.data.users :refer :all]
            [metabase.test.util :as tu]))


(defn remove-ids-and-timestamps [m]
  (let [f (fn [v]
            (cond
              (map? v) (remove-ids-and-timestamps v)
              (coll? v) (mapv remove-ids-and-timestamps v)
              :else v))]
    (into {} (for [[k v] m]
               (when-not (or (= :id k)
                             (.endsWith (name k) "_id")
                             (= :created_at k)
                             (= :updated_at k))
                 [k (f v)])))))


;; retrieve-dashboard-card
;; basic dashcard (no additional series)
(expect
  {:sizeX  2
   :sizeY  2
   :col    nil
   :row    nil
   :series []}
  (tu/with-temp* [Dashboard     [{dashboard-id :id}]
                  Card          [{card-id :id}]
                  DashboardCard [{dashcard-id :id} {:dashboard_id dashboard-id, :card_id card-id}]]
    (remove-ids-and-timestamps (retrieve-dashboard-card dashcard-id))))

;; retrieve-dashboard-card
;; dashcard w/ additional series
(expect
  {:sizeX  2
   :sizeY  2
   :col    nil
   :row    nil
   :series [{:name                   "Additional Series Card 1"
             :description            nil
             :display                :table
             :dataset_query          {}
             :visualization_settings {}}
            {:name                   "Additional Series Card 2"
             :description            nil
             :display                :table
             :dataset_query          {}
             :visualization_settings {}}]}
  (tu/with-temp* [Dashboard           [{dashboard-id :id}]
                  Card                [{card-id :id}]
                  Card                [{series-id-1 :id} {:name "Additional Series Card 1"}]
                  Card                [{series-id-2 :id} {:name "Additional Series Card 2"}]
                  DashboardCard       [{dashcard-id :id} {:dashboard_id dashboard-id, :card_id card-id}]
                  DashboardCardSeries [_                 {:dashboardcard_id dashcard-id, :card_id series-id-1, :position 0}]
                  DashboardCardSeries [_                 {:dashboardcard_id dashcard-id, :card_id series-id-2, :position 1}]]
    (remove-ids-and-timestamps (retrieve-dashboard-card dashcard-id))))


;; update-dashboard-card-series
(expect
  [[]
   ["card1"]
   ["card2"]
   ["card2" "card1"]
   ["card1" "card3"]]
  (tu/with-temp* [Dashboard     [{dashboard-id :id} {:name         "Test Dashboard"
                                                     :public_perms 0
                                                     :creator_id   (user->id :rasta)}]
                  Card          [{card-id :id}]
                  DashboardCard [{dashcard-id :id} {:dashboard_id dashboard-id, :card_id card-id}]
                  Card          [{card-id-1 :id} {:name "card1"}]
                  Card          [{card-id-2 :id} {:name "card2"}]
                  Card          [{card-id3 :id} {:name "card3"}]]
    (let [upd-series (fn [series]
                       (update-dashboard-card-series {:id dashcard-id} series)
                       (->> (db/sel-field [DashboardCardSeries :card_id] :dashboardcard_id dashcard-id)
                            (mapv (fn [card_id]
                                    (db/sel-1-field [Card :name] :id card_id)))))]
      [(upd-series [])
       (upd-series [card-id-1])
       (upd-series [card-id-2])
       (upd-series [card-id-2 card-id-1])
       (upd-series [card-id-1 card-id3])])))


;; create-dashboard-card
;; simple example with a single card
(expect
  [{:sizeX  4
    :sizeY  3
    :col    1
    :row    1
    :series [{:name                   "Test Card"
              :description            nil
              :display                :table
              :dataset_query          {}
              :visualization_settings {}}]}
   {:sizeX  4
    :sizeY  3
    :col    1
    :row    1
    :series [{:name                   "Test Card"
              :description            nil
              :display                :table
              :dataset_query          {}
              :visualization_settings {}}]}]
  (tu/with-temp* [Dashboard [{dashboard-id :id}]
                  Card      [{card-id :id} {:name "Test Card"}]]
    (let [dashboard-card (create-dashboard-card {:creator_id   (user->id :rasta)
                                                 :dashboard_id dashboard-id
                                                 :card_id      card-id
                                                 :sizeX        4
                                                 :sizeY        3
                                                 :row          1
                                                 :col          1
                                                 :series       [card-id]})]
      ;; first result is return value from function, second is to validate db captured everything
      [(remove-ids-and-timestamps dashboard-card)
       (remove-ids-and-timestamps (retrieve-dashboard-card (:id dashboard-card)))])))

;; update-dashboard-card
;; basic update.  we are testing multiple things here
;;  1. ability to update all the normal attributes for size/position
;;  2. ability to update series and ensure proper ordering
;;  3. ensure the card_id cannot be changed
;;  4. ensure the dashboard_id cannot be changed
(expect
  [{:sizeX  2
    :sizeY  2
    :col    nil
    :row    nil
    :series []}
   {:sizeX  4
    :sizeY  3
    :col    1
    :row    1
    :series [{:name                   "Test Card 2"
              :description            nil
              :display                :table
              :dataset_query          {}
              :visualization_settings {}}
             {:name                   "Test Card 1"
              :description            nil
              :display                :table
              :dataset_query          {}
              :visualization_settings {}}]}
   {:sizeX  4
    :sizeY  3
    :col    1
    :row    1
    :series [{:name                   "Test Card 2"
              :description            nil
              :display                :table
              :dataset_query          {}
              :visualization_settings {}}
             {:name                   "Test Card 1"
              :description            nil
              :display                :table
              :dataset_query          {}
              :visualization_settings {}}]}]
  (tu/with-temp* [Dashboard     [{dashboard-id :id}]
                  Card          [{card-id :id}]
                  DashboardCard [{dashcard-id :id} {:dashboard_id dashboard-id, :card_id card-id}]
                  Card          [{card-id-1 :id}   {:name "Test Card 1"}]
                  Card          [{card-id-2 :id}   {:name "Test Card 2"}]]
    ;; first result is the unmodified dashcard
    ;; second is the return value from the update call
    ;; third is to validate db captured everything
    [(remove-ids-and-timestamps (retrieve-dashboard-card dashcard-id))
     (remove-ids-and-timestamps (update-dashboard-card {:id           dashcard-id
                                                        :actor_id     (user->id :rasta)
                                                        :dashboard_id nil
                                                        :card_id      nil
                                                        :sizeX        4
                                                        :sizeY        3
                                                        :row          1
                                                        :col          1
                                                        :series       [card-id-2 card-id-1]}))
     (remove-ids-and-timestamps (retrieve-dashboard-card dashcard-id))]))
