(ns dactyl-keyboard.common
  (:refer-clojure :exclude [use import])
  (:require [clojure.core.matrix :refer [mmul]]
            [scad-clj.scad :refer :all]
            [scad-clj.model :refer :all]))

; common parts between the two boards.

(def extra-width
  "extra width between two keys in a row."
  2.5)
(def extra-height
  "extra height between two keys in a column."
  1.0)

(def keyswitch-height
  "the y dimension of an mx style keyswitch, in millimeter."
  14.0)
(def keyswitch-width
  "the x dimension of an mx style keyswitch, in millimeter."
  14.0)

(def alps-width
  "the x dimension of an alps style keyswitch, in millimeter."
  15.6)
(def alps-notch-width
  15.5)
(def alps-notch-height
  1)
(def alps-height
  "the y dimension of an alps style keyswitch, in millimeter."
  13)

(def sa-profile-key-height 12.7)

(def plate-thickness 5)
(def mount-width (+ keyswitch-width 3.5))
(def mount-height (+ keyswitch-height 3.5))

(def cap-top-height (+ plate-thickness sa-profile-key-height))

;;;;;;;;;;;;;;;;;
;; placement function ;;
;;;;;;;;;;;;;;;;;

(defn dm-column-offset
  "Determines how much 'stagger' the columns are for dm.
   0 = inner index finger's column.
   1 = index finger's column.
   2 = middle finger's column.
   3 = ring finger's column.
   4 >= pinky finger's column.
   [x y z] means that it will be staggered by 'x'mm in X axis (left/right),
   'y'mm in Y axis (front/back), and 'z'mm in Z axis (up/down). "
  [ortho? column]
  (if ortho?
    (cond (= column 2)  [0   0    0]
          (>= column 4) [0   0     0]
          :else         [0   0     0])
    (cond (= column 2)  [0   2.82 -6.5]
          (>= column 4) [0  -13    6]
          :else         [0   0     0])))

(defn fcenterrow
  "Determines where should the center (bottom-most point in the row's curve)
   of the row located at. And most people would want to have the center
   at the homerow. Why does it subtract the value by 3? Because this codebase
   starts the row from the higher row (F row -> num row -> top row)
   and the homerow is number 3 from the last after thumb and bottom row."
  [nrows]
  (- nrows 1))

(defn flastrow
  "Determines where the last row should be located at."
  [nrows]
  (- nrows 1))
(defn fcornerrow
  "Determines where the penultimate row should be located at."
  [nrows]
  (- nrows 2))
(defn fmiddlerow
  "Should be replaced with `fcenterrow`."
  [nrows]
  (- nrows 3))
(defn flastcol
  "Determines where the last column should be located at. With 0 being inner index
   finger, 1 being index finger, and so on."
  [ncols]
  (- ncols 1))

(defn frow-radius
  "It computes the radius of the row's curve. It takes the value of `pi` divided
   by `alpha` to compute the said radius."
  [alpha]
  (+ (/ (/ (+ mount-height extra-height) 2)
        (Math/sin (/ alpha 2)))
     cap-top-height))

(defn fcolumn-radius
  "It computes the radius of the column's curve. It takes the value of `pi` divided
   by `beta` to compute the said radius."
  [beta]
  (+ (/ (/ (+ mount-width extra-width) 2)
        (Math/sin (/ beta 2)))
     cap-top-height))

; when set `use-wide-pinky?`,
; you will get 1.5u keys for the outermost pinky keys.
(defn offset-for-column
  "This function is used to give additional spacing for the column.
   Main use case is to make the outer pinky keys use 1.5u."
  [c col row]
  (let [use-wide-pinky? (get c :configuration-use-wide-pinky?)
        nrows (get c :configuration-nrows 5)
        ncols (get c :configuration-ncols)
        lastrow (flastrow nrows)
        lastcol (flastcol ncols)]
    (if (and use-wide-pinky?
             (not= row lastrow)
             (= col lastcol))
      5.5
      0)))

; this is the helper function to 'place' the keys on the defined curve
; of the board.
(defn apply-key-geometry
  "Helps to place the keys in the determined where a key should be placed
   and rotated in xyz coordinate based on its position (row and column).
   It is the implementation detail of `key-place`."
  [c translate-fn rotate-x-fn rotate-y-fn column row shape]
  (let [alpha             (get c :configuration-alpha)
        beta              (get c :configuration-beta)
        centercol         (get c :configuration-centercol 2)
        centerrow         (fcenterrow (get c :configuration-nrows 5))
        ortho?            (get c :configuration-ortho?)
        tenting-angle     (get c :configuration-tenting-angle)
        keyboard-z-offset (get c :configuration-z-offset)
        column-angle      (* beta (- centercol column))
        placed-shape      (->> shape
                               (translate-fn [(offset-for-column c
                                                                 column
                                                                 row)
                                              0
                                              (- (frow-radius alpha))])
                               (rotate-x-fn  (* alpha (- centerrow row)))
                               (translate-fn [0 0 (frow-radius alpha)])
                               (translate-fn [0 0 (- (fcolumn-radius beta))])
                               (rotate-y-fn  column-angle)
                               (translate-fn [0 0 (fcolumn-radius beta)])
                               (translate-fn (dm-column-offset ortho? column)))]
    (->> placed-shape
         (rotate-y-fn  tenting-angle)
         (translate-fn [0 0 keyboard-z-offset]))))

(defn rotate-around-x [angle position]
  (mmul
   [[1 0 0]
    [0 (Math/cos angle) (- (Math/sin angle))]
    [0 (Math/sin angle)    (Math/cos angle)]]
   position))

(defn rotate-around-y [angle position]
  (mmul
   [[(Math/cos angle)     0 (Math/sin angle)]
    [0                    1 0]
    [(- (Math/sin angle)) 0 (Math/cos angle)]]
   position))

(def left-wall-x-offset 10)
(def left-wall-z-offset  3)

(defn key-position
  "determines the position of a key based on the
  configuration, column, row, and position.
  it takes configuration to determine whether it is the last column
  and some other options like whether it's a part of a staggered board
  or not."
  [c column row position]
  (apply-key-geometry c
                      (partial map +)
                      rotate-around-x
                      rotate-around-y
                      column
                      row
                      position))

(defn left-key-position
  "determines the position of the left column key position."
  [c row direction]
  (map -
       (key-position c 0 row [(* mount-width -0.5) (* direction mount-height 0.5) 0])
       [left-wall-x-offset 0 left-wall-z-offset]))

;;;;;;;;;;;;;;;;;
;; Switch Hole ;;
;;;;;;;;;;;;;;;;;
(defn single-plate
  "Defines the form of switch hole. It determines the whether it uses
   box or mx style based on the `configuration-create-side-nub?`. It also
   asks whether it creates hotswap housing or not based on `configuration-use-hotswap?`.
   and determines whether it should use alps cutout or not based on  `configuration-use-alps?`"
  [c]
  (let [create-side-nub?    (get c :configuration-create-side-nub?)
        use-hotswap?        (get c :configuration-use-hotswap?)
        use-alps?           (get c :configuration-use-alps?)
        plate-projection?   (get c :configuration-plate-projection? false)
        alps-fill-in        (translate [0 0 (/ plate-thickness 2)] (cube alps-width alps-height plate-thickness))
        mx-fill-in          (translate [0 0 (/ plate-thickness 2)] (cube keyswitch-width keyswitch-height plate-thickness))
        fill-in             (if use-alps? alps-fill-in mx-fill-in)
        top-wall            (if use-alps?
                              (->> (cube (+ keyswitch-width 3) 2.7 plate-thickness)
                                   (translate [0
                                               (+ (/ 2.7 2) (/ alps-height 2))
                                               (/ plate-thickness 2)]))
                              (->> (cube (+ keyswitch-width 3) 2 plate-thickness)
                                   (translate [0
                                               (+ (/ 2 2) (/ keyswitch-height 2))
                                               (/ plate-thickness 2)])))
        left-wall           (if use-alps?
                              (union (->> (cube 2 (+ keyswitch-height 3) plate-thickness)
                                          (translate [(+ (/ 2 2) (/ 15.6 2))
                                                      0
                                                      (/ plate-thickness 2)]))
                                     (->> (cube 1.5 (+ keyswitch-height 3) 1.0)
                                          (translate [(+ (/ 1.5 2) (/ alps-notch-width 2))
                                                      0
                                                      (- plate-thickness
                                                         (/ alps-notch-height 2))])))
                              (->> (cube 2 (+ keyswitch-height 3) plate-thickness)
                                   (translate [(+ (/ 2 2) (/ keyswitch-width 2))
                                               0
                                               (/ plate-thickness 2)])))
        side-nub            (->> (binding [*fn* 30] (cylinder 1 2.75))
                                 (rotate (/ pi 2) [1 0 0])
                                 (translate [(+ (/ keyswitch-width 2)) 0 1])
                                 (hull (->> (cube 1.5 2.75 plate-thickness)
                                            (translate [(+ (/ 1.5 2) (/ keyswitch-width 2))
                                                        0
                                                        (/ plate-thickness 2)]))))
        ; the hole's wall.
        plate-half          (union top-wall
                                   left-wall
                                   (if create-side-nub? (with-fn 100 side-nub) ()))
        ; the bottom of the hole.
        swap-holder         (->> (cube (+ keyswitch-width 3) (/ (+ keyswitch-height 3) 2) 3)
                                 (translate [0 (/ (+ keyswitch-height 3) 4) -1.5]))
        ; for the main axis
        main-axis-hole      (->> (cylinder (/ 4.0 2) 10)
                                 (with-fn 12))
        plus-hole           (->> (cylinder (/ 3.3 2) 10)
                                 (with-fn 8)
                                 (translate [-3.81 2.54 0]))
        minus-hole          (->> (cylinder (/ 3.3 2) 10)
                                 (with-fn 8)
                                 (translate [2.54 5.08 0]))
        plus-hole-mirrored  (->> (cylinder (/ 3.3 2) 10)
                                 (with-fn 8)
                                 (translate [3.81 2.54 0]))
        minus-hole-mirrored (->> (cylinder (/ 3.3 2) 10)
                                 (with-fn 8)
                                 (translate [-2.54 5.08 0]))
        friction-hole       (->> (cylinder (/ 1.7 2) 10)
                                 (with-fn 8))
        friction-hole-right (translate [5 0 0] friction-hole)
        friction-hole-left  (translate [-5 0 0] friction-hole)
        hotswap-base-shape  (->> (cube 19 6.2 3.5)
                                 (translate [0 4 -2.6]))
        hotswap-holder      (difference swap-holder
                                        main-axis-hole
                                        plus-hole
                                        minus-hole
                                        plus-hole-mirrored
                                        minus-hole-mirrored
                                        friction-hole-left
                                        friction-hole-right
                                        hotswap-base-shape)]
    (difference (union plate-half
                       (->> plate-half
                            (mirror [1 0 0])
                            (mirror [0 1 0]))
                       (if plate-projection? fill-in ())
                       (if (and use-hotswap?
                                (not use-alps?))
                         hotswap-holder
                         ())))))
;;;;;;;;;;;;;;;;
;; SA Keycaps ;;
;;;;;;;;;;;;;;;;

(def sa-length 18.25)
(def sa-double-length 37.5)
(def sa-cap
  {1   (let [bl2     (/ 18.5 2)
             m       (/ 17 2)
             key-cap (hull (->> (polygon [[bl2 bl2] [bl2 (- bl2)] [(- bl2) (- bl2)] [(- bl2) bl2]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 0.05]))
                           (->> (polygon [[m m] [m (- m)] [(- m) (- m)] [(- m) m]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 6]))
                           (->> (polygon [[6 6] [6 -6] [-6 -6] [-6 6]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 12])))]
         (->> key-cap
              (translate [0 0 (+ 5 plate-thickness)])
              (color [220/255 163/255 163/255 1])))
   2   (let [bl2     (/ sa-double-length 2)
             bw2     (/ 18.25 2)
             key-cap (hull (->> (polygon [[bw2 bl2] [bw2 (- bl2)] [(- bw2) (- bl2)] [(- bw2) bl2]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 0.05]))
                           (->> (polygon [[6 16] [6 -16] [-6 -16] [-6 16]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 12])))]
         (->> key-cap
              (translate [0 0 (+ 5 plate-thickness)])
              (color [127/255 159/255 127/255 1])))
   1.5 (let [bl2     (/ 18.25 2)
             bw2     (/ 28 2)
             key-cap (hull (->> (polygon [[bw2 bl2] [bw2 (- bl2)] [(- bw2) (- bl2)] [(- bw2) bl2]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 0.05]))
                           (->> (polygon [[11 6] [-11 6] [-11 -6] [11 -6]])
                                (extrude-linear {:height    0.1
                                                 :twist     0
                                                 :convexity 0})
                                (translate [0 0 12])))]
         (->> key-cap
              (translate [0 0 (+ 5 plate-thickness)])
              (color [240/255 223/255 175/255 1])))})

(def web-thickness 7)
(def post-size 0.1)
(def web-post
  (->> (cube post-size post-size web-thickness)
       (translate [0 0 (+ (/ web-thickness -2)
                          plate-thickness)])))

(def post-adj (/ post-size 2))
(def web-post-tr (translate [(- (/ mount-width 2) post-adj) (- (/ mount-height 2) post-adj) 0] web-post))
(def web-post-tl (translate [(+ (/ mount-width -2) post-adj) (- (/ mount-height 2) post-adj) 0] web-post))
(def web-post-bl (translate [(+ (/ mount-width -2) post-adj) (+ (/ mount-height -2) post-adj) 0] web-post))
(def web-post-br (translate [(- (/ mount-width 2) post-adj) (+ (/ mount-height -2) post-adj) 0] web-post))

; length of the first downward-sloping part of the wall (negative)
(def wall-z-offset -15)
; offset in the x and/or y direction for the first downward-sloping part of the wall (negative)
(def wall-xy-offset 5)
; wall thickness parameter; originally 5
(def wall-thickness 3)

(defn wall-locate1 [dx dy]
  [(* dx wall-thickness) (* dy wall-thickness) -1])
(defn wall-locate2 [dx dy]
  [(* dx wall-xy-offset) (* dy wall-xy-offset) wall-z-offset])
(defn wall-locate3 [dx dy]
  [(* dx (+ wall-xy-offset wall-thickness))
   (* dy (+ wall-xy-offset wall-thickness))
   wall-z-offset])
;; connectors
(def rj9-cube
  (cube 14.78 13 22.38))
(defn rj9-position
  "determines the position of the rj9 housing.
  it takes a function that generates the coordinate of the housing
  and the configuration."
  [frj9-start c]
  [(first (frj9-start c)) (second (frj9-start c)) 11])
(defn rj9-space
  "puts the space of the rj9 housing based on function and configuration
  that is provided."
  [frj9-start c]
  (translate (rj9-position frj9-start c) rj9-cube))
(defn rj9-holder
  "TODO: doc"
  [frj9-start c]
  (translate
   (rj9-position frj9-start c)
   (difference rj9-cube
               (union (translate [0 2 0] (cube 10.78  9 18.38))
                      (translate [0 0 5] (cube 10.78 13  5))))))

(def usb-holder-size [6.5 10.0 13.6])
(def usb-holder-thickness 4)
(defn usb-holder
  "TODO: doc"
  [fusb-holder-position c]
  (->> (cube (+ (first usb-holder-size) usb-holder-thickness)
             (second usb-holder-size)
             (+ (last usb-holder-size) usb-holder-thickness))
       (translate [(first (fusb-holder-position c))
                   (second (fusb-holder-position c))
                   (/ (+ (last usb-holder-size) usb-holder-thickness) 2)])))
(defn usb-holder-hole
  "TODO: doc"
  [fusb-holder-position c]
  (->> (apply cube usb-holder-size)
       (translate [(first (fusb-holder-position c))
                   (second (fusb-holder-position c))
                   (/ (+ (last usb-holder-size) usb-holder-thickness) 2)])))

(defn screw-insert-shape [bottom-radius top-radius height]
  (union (cylinder [bottom-radius top-radius] height)
         (translate [0 0 (/ height 2)] (sphere top-radius))))

(def screw-insert-height 9)
(def screw-insert-bottom-radius (/ 5.31 2))
(def screw-insert-top-radius (/ 5.1 2))

(defn screw-insert-holes
  "TODO: doc.
   but basically it takes a function that places screw holes with the following specs."
  [placement-function c]
  (placement-function c
                      screw-insert-bottom-radius
                      screw-insert-top-radius
                      screw-insert-height))
(defn screw-insert-outers
  "TODO: doc.
   but basically it takes a function that places outer parts of screw holes with the following specs."
  [placement-function c]
  (placement-function c
                      (+ screw-insert-bottom-radius 1.6)
                      (+ screw-insert-top-radius 1.6)
                      (+ screw-insert-height 1.5)))
(defn screw-insert-screw-holes
  "TODO: doc."
  [placement-function c]
  (placement-function c 1.7 1.7 350))

(defn screw-insert [c column row bottom-radius top-radius height]
  (let [lastcol     (flastcol (get c :configuration-ncols))
        lastrow     (flastrow (get c :configuration-nrows 5))
        shift-right (= column lastcol)
        shift-left  (= column 0)
        shift-up    (and (not (or shift-right shift-left)) (= row 0))
        shift-down  (and (not (or shift-right shift-left)) (>= row lastrow))
        position    (if shift-up
                      (key-position c column row (map + (wall-locate2  0  1) [0 (/ mount-height 2) 0]))
                      (if shift-down
                        (key-position c column row (map - (wall-locate2  0 -1) [0 (/ mount-height 2) 0]))
                        (if shift-left
                          (map + (left-key-position c row 0) (wall-locate3 -1 0))
                          (key-position c column row (map + (wall-locate2  1  0) [(/ mount-width 2) 0 0])))))]
    (->> (screw-insert-shape bottom-radius top-radius height)
         (translate [(first position) (second position) (/ height 2)]))))

(def wrist-rest-back-height 29)
(def wrist-rest-angle 0)
(def wrist-rest-rotation-angle 0)
(def wrist-rest-ledge 3.5)
(defn wrist-rest-y-angle [tenting-angle] (* tenting-angle 45))

;;Wrist rest to case connections
(defn left-wrist-connector-x [ncols] (if (> ncols 5) -40 -40))
(defn middle-wrist-connector-x [ncols] (if (> ncols 5) -12 -17.5))
(defn right-wrist-connector-x [ncols] (if (> ncols 5) 24 5))
(def wrist-right-nut-y 20.5)
(def wrist-base-position-x -1)

(def wrist-rest-front-cut
  (scale [1.1, 1, 1]
         (->> (cylinder 7 200)
              (with-fn 50)
              (translate [0 -13.4 0]))))

(def cut-bottom
  (->> (cube 300 300 100) (translate [0 0 -50])))

(def h-offset
  (* (Math/tan (/ (* pi wrist-rest-angle) 180)) 88))

(def scale-cos
  (Math/cos (/ (* pi wrist-rest-angle) 180)))

(def scale-amount
  (/ (* 83.7 scale-cos) 19.33))

(def wrist-rest
  (difference
   (scale [4.25 scale-amount 1]
          (difference
           (union
            (difference
             (scale [1.3, 1, 1]
                    (->> (cylinder 10 150)
                         (with-fn 50)
                         (translate [0 0 0])))
             (scale [1.1, 1, 1]
                    (->> (cylinder 7 201)
                         (with-fn 50)
                         (translate [0 -13.4 0]))
                    (->> (cube 18 10 201)
                         (translate [0 -12.4 0]))))
            (->> (cylinder 6.8 200)
                 (with-fn 50)
                 (translate [-6.15 -0.98 0]))
            (->> (cylinder 6.8 200)
                 (with-fn 50)
                 (translate [6.15 -0.98 0]))
            (->> (cylinder 5.9 200)
                 (with-fn 50)
                 (translate [-6.35 -2 0]))
            (scale [1.01, 1, 1]
                   (->> (cylinder 5.9 200)
                        (with-fn 50)
                        (translate [6.35 -2. 0]))))))
   cut-bottom))

(defn wrist-rest-base [c]
  (let [tenting-angle (get c :configuration-tenting-angle)]
    (->>
     (scale [1 1 1] ;;;;scale the wrist rest to the final size after it has been cut
            (difference
             (scale [1.08 1.08 1] wrist-rest)
             (->> (cube 200 200 200)
                  (translate [0 0 (+ (+ (/ h-offset 2)
                                        (- wrist-rest-back-height h-offset))
                                     100)])
                  (rotate  (/ (* pi wrist-rest-angle) 180)  [1 0 0])
                  (rotate  (/ (* pi (wrist-rest-y-angle tenting-angle)) 180)  [1 1 0]))
             (->> (difference
                   wrist-rest
                   (->> (cube 200 200 200)
                        (translate [0 0 (- (+ (/ h-offset 2)
                                              (- wrist-rest-back-height h-offset))
                                           (+ 100  wrist-rest-ledge))])
                        (rotate (/ (* pi wrist-rest-angle) 180) [1 0 0])
                        (rotate (/ (* pi (wrist-rest-y-angle tenting-angle)) 180)  [1 1 0])))))))))

(defn rest-case-cuts [c]
  (let [ncols (get c :configuration-ncols)
        nrows (get c :configuration-nrows)]
    (union
     (->> (cylinder 1.85 25)
          (with-fn 30)
          (rotate  (/ pi 2)  [1 0 0])
          (translate [(right-wrist-connector-x ncols) 24 4.5]))
     (->> (cylinder 2.8 5.2)
          (with-fn 50)
          (rotate  (/ pi 2)  [1 0 0])
          (translate [(right-wrist-connector-x ncols) (+ 33.8 nrows) 4.5]))
     (->> (cube 6 3 12.2)
          (translate [(right-wrist-connector-x ncols) (+ wrist-right-nut-y nrows) 1.5]))
     (->> (cylinder 1.85 25)
          (with-fn 30)
          (rotate (/ pi 2)  [1 0 0])
          (translate [(middle-wrist-connector-x ncols) 14 4.5]))
     (->> (cylinder 2.8 5.2)
          (with-fn 50)
          (rotate (/ pi 2) [1 0 0])
          (translate [(middle-wrist-connector-x ncols) 26 4.5]))
     (->> (cube 6 3 12.2)
          (translate [(middle-wrist-connector-x ncols) (+ 10.0 nrows) 1.5]))
     (->> (cylinder 1.85 25)
          (with-fn 30)
          (rotate (/ pi 2) [1 0 0])
          (translate [(left-wrist-connector-x ncols) 11 4.5]))
     (->> (cylinder 2.8 5.2)
          (with-fn 50)
          (rotate (/ pi 2) [1 0 0])
          (translate [(left-wrist-connector-x ncols) (+ 17.25 nrows) 4.5]))
     (->> (cube 6 3 12.2)
          (translate [(left-wrist-connector-x ncols) (+ 6.0 nrows) 1.5])))))

(defn rest-case-connectors [c]
  (let [ncols (get c :configuration-ncols)]
    (difference
     (union
      (scale [1 1 1.6]
             (->> (cylinder 8 60)
                  (with-fn 50)
                  (rotate  (/ pi 2) [1 0 0])
                  (translate [(right-wrist-connector-x ncols) 14 4])))
      (scale [1 1 1.6]
             (->> (cylinder 8 60)
                  (with-fn 50)
                  (rotate (/ pi 2) [1 0 0])
                  (translate [(middle-wrist-connector-x ncols) 19 4])))
      (scale [1 1 1.6]
             (->> (cylinder 8 60)
                  (with-fn 50)
                  (rotate (/ pi 2) [1 0 0])
                  (translate [(left-wrist-connector-x ncols) 14 4])))))))
