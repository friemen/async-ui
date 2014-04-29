(ns async-ui.forml
  (:require [metam.core :refer :all]))

(declare defaults)

(defmetamodel forml
  (-> (make-hierarchy)
      (derive ::container ::component)
      (derive ::widget ::component)
      
      ; concrete component types
      (derive ::button ::widget)
      (derive ::label ::widget)
      (derive ::listbox ::labeled)
      (derive ::listbox ::widget)
      (derive ::listbox ::growing)
      (derive ::panel ::growing)
      (derive ::panel ::container)
      (derive ::textfield ::labeled)
      (derive ::textfield ::widget))
  {::button       {:text [string?]
                   :lyhint [string?]
                   :icon [string?]}
   ::label        {:text [string?]
                   :lyhint [string?]
                   :icon [string?]}
   ::listbox      {:label [string?]
                   :lyhint [string?]
                   :labelyhint [string?]}
   ::panel        {:lygeneral [string?]
                   :lycolumns [string?]
                   :lyrows [string?]
                   :lyhint [string?]
                   :components [(coll (type-of ::component))]}
   ::textfield    {:label [string?]
                   :lyhint [string?]
                   :labelyhint [string?]}
   ::window       {:title [string?]
                   :content [(type-of ::container)]
                   :owner []
                   :modality [(value-of :none :window :application)]}}
  #'defaults)


(defdefaults defaults forml
  {:default nil
   [::button :text]             (:name spec)
   [::growing :lyhint]          "grow"
   [::labeled :labelyhint]      ""
   [::labeled :label]           (:name spec)
   [::label :text]              (:name spec)
   [::panel :lyrows]            ""
   [::panel :lycolumns]         ""
   [::widget :lyhint]           ""
   [::window :title]            (:name spec)
   [::window :owner]            nil
   [::window :modality]         :none})

(prefer-method defaults [::growing :lyhint] [::widget :lyhint])
